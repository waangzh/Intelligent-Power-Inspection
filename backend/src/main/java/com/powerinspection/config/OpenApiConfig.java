package com.powerinspection.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI powerInspectionOpenApi() {
    return new OpenAPI()
      .info(new Info()
        .title("Power Inspection API")
        .description("电力智能巡检平台 API。权限码权威定义见 Permission 枚举；静态片段见 shared/generated/openapi-permissions.yaml")
        .version("1.0.0"));
  }
}
