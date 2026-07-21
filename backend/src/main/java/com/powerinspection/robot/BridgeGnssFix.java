package com.powerinspection.robot;

import java.time.Instant;

public record BridgeGnssFix(
    boolean valid,
    boolean stale,
    String frame,
    Double latitude,
    Double longitude,
    Double altitude,
    Integer quality,
    String fixType,
    Integer satellites,
    Double hdop,
    Double differentialAge,
    String baseStationId,
    Double ageSec,
    Instant observedAt
) {}
