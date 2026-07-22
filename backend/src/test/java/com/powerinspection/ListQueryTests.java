package com.powerinspection;

import static org.assertj.core.api.Assertions.assertThat;

import com.powerinspection.common.ListQuery;
import org.junit.jupiter.api.Test;

class ListQueryTests {
  @Test
  void exposesDetectionSourceFilters() {
    ListQuery query = new ListQuery();
    query.setSourceType("DETECTION_RUN");
    query.setDetectionRunId("run-1");
    query.setImageId("image-1");
    query.setCheckpointId("cp-1");
    query.setItemId("fire-risk");

    assertThat(query.filters("sourceType", "detectionRunId", "imageId", "checkpointId", "itemId"))
        .containsExactly(
            org.assertj.core.data.MapEntry.entry("sourceType", "DETECTION_RUN"),
            org.assertj.core.data.MapEntry.entry("detectionRunId", "run-1"),
            org.assertj.core.data.MapEntry.entry("imageId", "image-1"),
            org.assertj.core.data.MapEntry.entry("checkpointId", "cp-1"),
            org.assertj.core.data.MapEntry.entry("itemId", "fire-risk"));
  }
}
