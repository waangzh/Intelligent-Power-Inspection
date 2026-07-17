package com.powerinspection.model;

import java.util.List;

public record LocateAnythingResult(
  List<LocateAnythingFinding> findings,
  List<String> warnings,
  String resultImageUrl
) {
  public LocateAnythingResult {
    findings = findings == null ? List.of() : List.copyOf(findings);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
