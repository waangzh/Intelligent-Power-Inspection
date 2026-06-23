package com.powerinspection.model;

import java.util.Map;

public interface LingBotMapGateway {
  Map<String, Object> createJob(Map<String, Object> payload);

  Map<String, Object> advanceJob(Map<String, Object> job);

  Map<String, Object> pointCloud(Map<String, Object> job);
}
