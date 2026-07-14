package com.powerinspection.route;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.route-deployment")
public class RouteDeploymentProperties {
  private int workerDelayMs = 3000;
  private int workerInitialDelayMs = 5000;
  private int workerBatchSize = 10;
  private int maxAttempts = 5;
  private int initialBackoffSeconds = 5;
  private int maxBackoffSeconds = 300;

  public int getWorkerDelayMs() { return workerDelayMs; }
  public void setWorkerDelayMs(int workerDelayMs) { this.workerDelayMs = workerDelayMs; }
  public int getWorkerInitialDelayMs() { return workerInitialDelayMs; }
  public void setWorkerInitialDelayMs(int workerInitialDelayMs) { this.workerInitialDelayMs = workerInitialDelayMs; }
  public int getWorkerBatchSize() { return workerBatchSize; }
  public void setWorkerBatchSize(int workerBatchSize) { this.workerBatchSize = workerBatchSize; }
  public int getMaxAttempts() { return maxAttempts; }
  public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
  public int getInitialBackoffSeconds() { return initialBackoffSeconds; }
  public void setInitialBackoffSeconds(int initialBackoffSeconds) { this.initialBackoffSeconds = initialBackoffSeconds; }
  public int getMaxBackoffSeconds() { return maxBackoffSeconds; }
  public void setMaxBackoffSeconds(int maxBackoffSeconds) { this.maxBackoffSeconds = maxBackoffSeconds; }
}
