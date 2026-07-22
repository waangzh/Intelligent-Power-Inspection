package com.powerinspection.site;

import com.powerinspection.common.ApiException;
import java.util.Map;

final class SiteCoordinateValidator {
  private SiteCoordinateValidator() {}

  static void validateForCreate(Map<String, Object> body) {
    validate(body, true);
  }

  static void validateForUpdate(Map<String, Object> body) {
    validate(body, false);
  }

  private static void validate(Map<String, Object> body, boolean required) {
    if (!body.containsKey("center")) {
      if (required) {
        throw ApiException.badRequest("站点中心坐标不能为空");
      }
      return;
    }

    Object rawCenter = body.get("center");
    if (!(rawCenter instanceof Map<?, ?> center)) {
      throw ApiException.badRequest("站点中心坐标必须包含数值类型的 lat 和 lng");
    }

    Double latitude = finiteNumber(center.get("lat"));
    Double longitude = finiteNumber(center.get("lng"));
    if (latitude == null || longitude == null) {
      throw ApiException.badRequest("站点中心坐标必须包含数值类型的 lat 和 lng");
    }

    if (suspectedSwap(latitude, longitude)) {
      throw ApiException.badRequest(
        "中心坐标疑似经纬度填写反了：纬度应在 -90 到 90 之间，经度应在 -180 到 180 之间；请核对后重新填写，系统不会自动交换"
      );
    }
    if (latitude < -90 || latitude > 90) {
      throw ApiException.badRequest("中心纬度必须在 -90 到 90 之间");
    }
    if (longitude < -180 || longitude > 180) {
      throw ApiException.badRequest("中心经度必须在 -180 到 180 之间");
    }
  }

  private static Double finiteNumber(Object value) {
    if (!(value instanceof Number number)) return null;
    double result = number.doubleValue();
    return Double.isFinite(result) ? result : null;
  }

  private static boolean suspectedSwap(double latitude, double longitude) {
    return Math.abs(latitude) > 90
      && Math.abs(latitude) <= 180
      && Math.abs(longitude) <= 90;
  }
}
