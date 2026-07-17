package com.powerinspection.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  @GetMapping("/api/v1/health")
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.ok(Map.of("ok", true, "service", "power-inspection-backend"));
  }
}
