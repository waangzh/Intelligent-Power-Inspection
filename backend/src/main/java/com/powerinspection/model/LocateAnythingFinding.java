package com.powerinspection.model;

import java.util.List;
import java.util.Map;

public record LocateAnythingFinding(
  String type,
  String prompt,
  double score,
  List<Integer> bbox,
  String label,
  String imageUrl,
  Map<String, Object> rawResult
) {
}
