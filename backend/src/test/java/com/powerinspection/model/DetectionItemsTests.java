package com.powerinspection.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DetectionItemsTests {
  @Test
  void disabledItemsAreNotSentToTheModel() {
    List<Map<String, Object>> enabled = DetectionItems.enabled(List.of(
      Map.of("type", "SWITCH", "enabled", true),
      Map.of("type", "METER", "enabled", false),
      Map.of("type", "FIRE")
    ));

    assertThat(enabled).extracting(item -> item.get("type"))
      .containsExactly("SWITCH", "FIRE");
    assertThat(enabled).extracting(item -> item.get("displayLabel"))
      .containsExactly("刀闸开关", "明火烟雾");
  }

  @Test
  void explicitDisplayLabelIsPreserved() {
    List<Map<String, Object>> enabled = DetectionItems.enabled(List.of(
      Map.of("type", "SWITCH", "enabled", true, "displayLabel", "红色刀闸开关")
    ));

    assertThat(enabled.get(0)).containsEntry("displayLabel", "红色刀闸开关");
  }
}
