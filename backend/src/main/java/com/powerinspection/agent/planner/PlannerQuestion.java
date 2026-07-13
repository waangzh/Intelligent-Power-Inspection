package com.powerinspection.agent.planner;

import java.util.List;

public record PlannerQuestion(String type, String prompt, List<String> options) {
  public PlannerQuestion {
    options = options == null ? List.of() : List.copyOf(options);
  }
}
