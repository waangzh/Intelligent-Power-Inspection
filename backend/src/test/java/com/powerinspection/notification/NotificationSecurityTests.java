package com.powerinspection.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSecurityTests {
  private static final String ADMIN_NOTIFICATION = "ntf_security_admin";
  private static final String GLOBAL_NOTIFICATION = "ntf_security_global";

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  DataStoreService dataStore;

  @Autowired
  NotificationRecipientRepository recipientRepository;

  @BeforeEach
  void setUp() {
    recipientRepository.deleteAll();
    removeIfPresent(ADMIN_NOTIFICATION);
    removeIfPresent(GLOBAL_NOTIFICATION);
    dataStore.upsert(DataCategory.NOTIFICATION, notification(
      ADMIN_NOTIFICATION, "user_admin", "管理员私有通知"
    ));
    dataStore.upsert(DataCategory.NOTIFICATION, notification(
      GLOBAL_NOTIFICATION, "*", "全局通知"
    ));
  }

  @Test
  void anotherUserCannotReadMutateOrDeletePrivateNotification() throws Exception {
    String dispatcherToken = login("dispatcher", "Disp@123");
    String adminToken = login("admin", "Admin@123");

    mockMvc.perform(get("/api/v1/notifications/" + ADMIN_NOTIFICATION)
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isNotFound());
    mockMvc.perform(patch("/api/v1/notifications/" + ADMIN_NOTIFICATION + "/read")
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/v1/notifications/" + ADMIN_NOTIFICATION)
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isNotFound());

    mockMvc.perform(delete("/api/v1/notifications/" + ADMIN_NOTIFICATION)
        .header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/notifications/" + ADMIN_NOTIFICATION)
        .header("Authorization", bearer(adminToken)))
      .andExpect(status().isNotFound());
    assertThat(dataStore.find(DataCategory.NOTIFICATION, ADMIN_NOTIFICATION)).isNotNull();
  }

  @Test
  void globalNotificationStateIsIndependentPerUser() throws Exception {
    String dispatcherToken = login("dispatcher", "Disp@123");
    String adminToken = login("admin", "Admin@123");

    String listed = mockMvc.perform(get("/api/v1/notifications")
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    JsonNode items = objectMapper.readTree(listed).path("data").path("items");
    assertThat(items.findValuesAsText("id")).contains(GLOBAL_NOTIFICATION).doesNotContain(ADMIN_NOTIFICATION);

    mockMvc.perform(patch("/api/v1/notifications/read-all")
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/notifications/" + GLOBAL_NOTIFICATION)
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.read").value(true));
    mockMvc.perform(get("/api/v1/notifications/" + GLOBAL_NOTIFICATION)
        .header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.read").value(false));

    mockMvc.perform(delete("/api/v1/notifications/" + GLOBAL_NOTIFICATION)
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/notifications/" + GLOBAL_NOTIFICATION)
        .header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/notifications/" + GLOBAL_NOTIFICATION)
        .header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk());

    assertThat(dataStore.find(DataCategory.NOTIFICATION, GLOBAL_NOTIFICATION)).isNotNull();
    assertThat(dataStore.find(DataCategory.NOTIFICATION, GLOBAL_NOTIFICATION).get("read")).isEqualTo(false);
  }

  private Map<String, Object> notification(String id, String userId, String title) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", id);
    item.put("userId", userId);
    item.put("type", "SYSTEM");
    item.put("title", title);
    item.put("content", "通知安全回归测试");
    item.put("read", false);
    return item;
  }

  private void removeIfPresent(String id) {
    if (dataStore.find(DataCategory.NOTIFICATION, id) != null) {
      dataStore.delete(DataCategory.NOTIFICATION, id);
    }
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":false}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
