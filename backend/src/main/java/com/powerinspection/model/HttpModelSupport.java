package com.powerinspection.model;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

final class HttpModelSupport {
  private HttpModelSupport() {
  }

  static RestClient restClient(String baseUrl, int timeoutSeconds, String serviceToken) {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
      .withConnectTimeout(Duration.ofSeconds(timeoutSeconds))
      .withReadTimeout(Duration.ofSeconds(timeoutSeconds));
    RestClient.Builder builder = RestClient.builder()
      .baseUrl(trimTrailingSlash(baseUrl))
      .requestFactory(ClientHttpRequestFactories.get(settings));
    if (StringUtils.hasText(serviceToken)) {
      builder.defaultHeader("X-Model-Service-Token", serviceToken);
    }
    builder.defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    return builder.build();
  }

  private static String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      throw new ModelServiceException("模型服务地址未配置");
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
