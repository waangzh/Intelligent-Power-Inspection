package com.powerinspection.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.sms.mode=mock", "app.sms.resend-interval-seconds=1"})
class AuthPasswordResetTests {
  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void resetPasswordBySmsThenLoginWithNewPassword() throws Exception {
    String username = "reset_user_01";
    String oldPassword = "OldPass123";
    String newPassword = "NewPass456";
    String phone = "13900001111";

    String registerCode = sendSms(phone, "REGISTER");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "username", username,
                        "password", oldPassword,
                        "confirmPassword", oldPassword,
                        "displayName", "重置测试",
                        "phone", phone,
                        "smsCode", registerCode,
                        "agreed", true)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", oldPassword, "remember", false)))
        .andExpect(status().isOk());

    String resetCode = sendSms(phone, "RESET_PASSWORD");
    mockMvc
        .perform(
            post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "phone", phone,
                        "smsCode", resetCode,
                        "newPassword", newPassword,
                        "confirmPassword", newPassword)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", oldPassword, "remember", false)))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", newPassword, "remember", false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.token").isNotEmpty());
  }

  @Test
  void sendResetSmsRejectsUnboundPhone() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("phone", "13900002222", "purpose", "RESET_PASSWORD")))
        .andExpect(status().isBadRequest());
  }

  private String sendSms(String phone, String purpose) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("phone", phone, "purpose", purpose)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.debugCode").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("debugCode").asText();
  }

  private String json(Object... values) throws Exception {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      map.put(String.valueOf(values[i]), values[i + 1]);
    }
    return objectMapper.writeValueAsString(map);
  }
}
