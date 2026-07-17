package com.powerinspection.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.powerinspection.model.ModelProperties;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.route.RouteDeploymentProperties;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, ModelProperties.class, RobotProperties.class, RouteDeploymentProperties.class})
public class PropertiesConfig {
}
