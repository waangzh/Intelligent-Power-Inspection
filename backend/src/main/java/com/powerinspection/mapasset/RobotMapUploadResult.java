package com.powerinspection.mapasset;

import java.util.Map;

public record RobotMapUploadResult(Map<String, Object> asset, boolean created) {
}
