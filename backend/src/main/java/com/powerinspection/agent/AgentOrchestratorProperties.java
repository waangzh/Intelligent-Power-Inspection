package com.powerinspection.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.agent.orchestration")
public class AgentOrchestratorProperties {
  private int maxSteps = 12;
  private int maxToolCalls = 8;
  private int maxVisionCalls = 2;
  private Duration runTimeout = Duration.ofMinutes(10);

  public int getMaxSteps() { return maxSteps; }
  public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
  public int getMaxToolCalls() { return maxToolCalls; }
  public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
  public int getMaxVisionCalls() { return maxVisionCalls; }
  public void setMaxVisionCalls(int maxVisionCalls) { this.maxVisionCalls = maxVisionCalls; }
  public Duration getRunTimeout() { return runTimeout; }
  public void setRunTimeout(Duration runTimeout) { this.runTimeout = runTimeout; }
}
