package com.powerinspection.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.model")
public class ModelProperties {
  private String mode = "http";
  private LocateAnything locateAnything = new LocateAnything();
  private Llm llm = new Llm();
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

  public Llm getLlm() {
    return llm;
  }

  public void setLlm(Llm llm) {
    this.llm = llm;
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

  public static class Llm {
    private String baseUrl = "http://127.0.0.1:9003";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 60;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }
}
