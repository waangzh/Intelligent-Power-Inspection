package com.powerinspection.sceneasset;

import java.util.Map;

public record RobotSceneUploadResult(Map<String, Object> asset, boolean created) {
}
