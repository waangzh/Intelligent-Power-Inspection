package com.powerinspection.model;

import java.util.List;

public interface LocateAnythingGateway {
  List<LocateAnythingFinding> detectCheckpoint(LocateAnythingRequest request);
}
