package com.powerinspection.record;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
public class RecordController {
  private final DataStoreService dataStore;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public RecordController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> records() {
    return ApiResponse.ok(dataStore.list(DataCategory.RECORD));
  }

  @PostMapping("/export")
  public ResponseEntity<byte[]> exportCsv() {
    permissionService.require(currentUser.get(), Permission.RECORD_EXPORT);
    StringBuilder csv = new StringBuilder("taskName,routeName,robotName,alarmCount,checkpointCount,duration,completedAt\n");
    for (Map<String, Object> record : dataStore.list(DataCategory.RECORD)) {
      csv.append(escape(record.get("taskName"))).append(',')
        .append(escape(record.get("routeName"))).append(',')
        .append(escape(record.get("robotName"))).append(',')
        .append(record.getOrDefault("alarmCount", 0)).append(',')
        .append(record.getOrDefault("checkpointCount", 0)).append(',')
        .append(escape(record.get("duration"))).append(',')
        .append(escape(record.get("completedAt"))).append('\n');
    }
    byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
      .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
      .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("inspection-records.csv", StandardCharsets.UTF_8).build().toString())
      .body(bytes);
  }

  private String escape(Object value) {
    String text = value == null ? "" : value.toString();
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
