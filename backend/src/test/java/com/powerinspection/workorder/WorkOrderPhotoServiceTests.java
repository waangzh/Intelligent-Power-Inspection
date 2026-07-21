package com.powerinspection.workorder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.powerinspection.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOrderPhotoServiceTests {

  @Test
  void rejectForeignPhotoUrl() {
    WorkOrderPhotoService service = new WorkOrderPhotoService(1024 * 1024);
    assertThatThrownBy(
            () ->
                service.normalizePhotoUrls(
                    "wo_test_1", List.of("/model-files/work-order-photos/other/abc.jpg")))
        .isInstanceOf(ApiException.class);
  }
}
