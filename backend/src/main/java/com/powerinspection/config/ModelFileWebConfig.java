package com.powerinspection.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ModelFileWebConfig implements WebMvcConfigurer {
  public static final Path MODEL_FILE_ROOT =
      Path.of("runtime-storage").toAbsolutePath().normalize();

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/model-files/**").addResourceLocations(modelFileRootLocation());
  }

  private String modelFileRootLocation() {
    String location = MODEL_FILE_ROOT.toUri().toString();
    return location.endsWith("/") ? location : location + "/";
  }
}
