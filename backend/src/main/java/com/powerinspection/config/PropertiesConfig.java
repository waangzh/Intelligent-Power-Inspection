package com.powerinspection.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.powerinspection.model.ModelProperties;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, ModelProperties.class})
public class PropertiesConfig {
}
