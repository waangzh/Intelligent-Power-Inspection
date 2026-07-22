package com.powerinspection.robot;

public record BridgePatrolSnapshot(
    String routeId,
    String targetId,
    String targetName,
    Integer targetIndex,
    Integer targetCount,
    String navigationPhase,
    Integer cycleIndex,
    Integer loopMaxCycles,
    Boolean loopInfinite,
    Double loopWaitRemainingSec,
    String lastError
) {}
