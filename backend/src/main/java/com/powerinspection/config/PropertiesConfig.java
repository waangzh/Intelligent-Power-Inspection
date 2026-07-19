package com.powerinspection.config;

import com.powerinspection.model.ModelProperties;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.route.RouteDeploymentProperties;
import com.powerinspection.sms.SmsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  JwtProperties.class,
  CorsProperties.class,
  ModelProperties.class,
  RobotProperties.class,
  RouteDeploymentProperties.class,
  SmsProperties.class
})
public class PropertiesConfig {}
