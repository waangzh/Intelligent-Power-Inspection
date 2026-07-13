package com.powerinspection.route;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteDocumentValidatorTests {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RouteDocumentValidator validator = new RouteDocumentValidator();

  @Test
  void acceptsSharedValidV3Fixtures() throws Exception {
    for (String fixture : new String[] {"minimal-v3.json", "extensions-v3.json", "keepout-v3.json"}) {
      assertTrue(validator.validate(read("valid", fixture)).isEmpty(), fixture + " 应通过校验");
    }
  }

  @Test
  void reportsExpectedIssueForSharedInvalidFixtures() throws Exception {
    Map<String, String> expectedCodes = Map.of(
      "invalid-map-sha.json", "INVALID_MAP_SHA256",
      "invalid-location.json", "INVALID_LOCATION",
      "duplicate-target-id.json", "DUPLICATE_TARGET_ID",
      "unknown-target-reference.json", "UNKNOWN_TARGET_REFERENCE",
      "multiple-routes.json", "MULTIPLE_ROUTES",
      "non-empty-schedules.json", "NON_EMPTY_SCHEDULES",
      "self-intersecting-polygon.json", "SELF_INTERSECTING_POLYGON",
      "zero-area-polygon.json", "ZERO_AREA_POLYGON",
      "invalid-mask-padding.json", "INVALID_MASK_PADDING"
    );
    for (Map.Entry<String, String> expectation : expectedCodes.entrySet()) {
      var issues = validator.validate(read("invalid", expectation.getKey()));
      assertTrue(issues.stream().anyMatch(issue -> expectation.getValue().equals(issue.code())), expectation.getKey() + " 应报告 " + expectation.getValue());
    }
  }

  private JsonNode read(String group, String fixture) throws Exception {
    Path path = Path.of("..", "contracts", "route-v3", group, fixture);
    return objectMapper.readTree(Files.readString(path));
  }
}
