package com.powerinspection.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.model")
public class ModelProperties {
  private String mode = "http";
  private LocateAnything locateAnything = new LocateAnything();
  private LingBotMap lingbotMap = new LingBotMap();
  private String serviceToken;

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public LocateAnything getLocateAnything() {
    return locateAnything;
  }

  public void setLocateAnything(LocateAnything locateAnything) {
    this.locateAnything = locateAnything;
  }

  public LingBotMap getLingbotMap() {
    return lingbotMap;
  }

  public void setLingbotMap(LingBotMap lingbotMap) {
    this.lingbotMap = lingbotMap;
  }

  public String getServiceToken() {
    return serviceToken;
  }

  public void setServiceToken(String serviceToken) {
    this.serviceToken = serviceToken;
  }

  public static class LocateAnything {
    private String baseUrl = "http://127.0.0.1:9001";
    private int timeoutSeconds = 900;
    private String generationMode = "fast";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public String getGenerationMode() {
      return generationMode;
    }

    public void setGenerationMode(String generationMode) {
      this.generationMode = generationMode;
    }
  }

  public static class LingBotMap {
    private String baseUrl = "http://127.0.0.1:9002";
    private int timeoutSeconds = 30;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }
}
