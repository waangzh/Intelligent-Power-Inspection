package com.powerinspection.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TokenVersionSecurityTests {
  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Autowired UserRepository userRepository;

  @Autowired TokenService tokenService;

  @Test
  void disabledUserCannotAccessReadApisWithOldAccessToken() throws Exception {
    String adminToken = login("admin", "Admin@123");
    String viewerToken = login("viewer", "View@123");
    String viewerId = userRepository.findByUsername("viewer").orElseThrow().getId();

    mockMvc
        .perform(get("/api/v1/sites").header("Authorization", "Bearer " + viewerToken))
        .andExpect(status().isOk());

    try {
      mockMvc
          .perform(
              patch("/api/v1/users/" + viewerId + "/enabled")
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":false}"))
          .andExpect(status().isOk());

      mockMvc
          .perform(get("/api/v1/sites").header("Authorization", "Bearer " + viewerToken))
          .andExpect(status().isUnauthorized());
      mockMvc
          .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + viewerToken))
          .andExpect(status().isUnauthorized());
      mockMvc
          .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + viewerToken))
          .andExpect(status().isUnauthorized());
    } finally {
      mockMvc
          .perform(
              patch("/api/v1/users/" + viewerId + "/enabled")
                  .header("Authorization", "Bearer " + adminToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"enabled\":true}"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void passwordChangeInvalidatesPreviousAccessToken() throws Exception {
    String username = "tv_pwd_" + System.currentTimeMillis();
    String phone = "1390000" + String.format("%04d", (int) (System.currentTimeMillis() % 10000));
    String smsCode = sendRegisterSmsCode(phone);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
          {
            "username":"%s",
            "password":"Tester123",
            "confirmPassword":"Tester123",
            "displayName":"Token Version",
            "phone":"%s",
            "smsCode":"%s",
            "agreed":true
          }
          """
                        .formatted(username, phone, smsCode)))
        .andExpect(status().isOk());

    String oldToken = login(username, "Tester123");
    mockMvc
        .perform(
            put("/api/v1/auth/password")
                .header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
          {
            "oldPassword":"Tester123",
            "newPassword":"Tester456",
            "confirmPassword":"Tester456"
          }
          """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/sites").header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isUnauthorized());

    String newToken = login(username, "Tester456");
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newToken))
        .andExpect(status().isOk());
  }

  @Test
  void logoutIncrementsTokenVersionAndRejectsOldAccessToken() throws Exception {
    String token = login("dispatcher", "Disp@123");
    long before = userRepository.findByUsername("dispatcher").orElseThrow().getTokenVersion();

    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    long after = userRepository.findByUsername("dispatcher").orElseThrow().getTokenVersion();
    org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before + 1);

    mockMvc
        .perform(get("/api/v1/sites").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void jwtWithoutMatchingTokenVersionIsRejected() {
    var viewer = userRepository.findByUsername("viewer").orElseThrow();
    String token = tokenService.create(viewer);
    viewer.incrementTokenVersion();
    userRepository.saveAndFlush(viewer);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> tokenService.validateUserToken(viewer, tokenService.claims(token)))
        .hasMessageContaining("登录状态已失效");
  }

  private String login(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\""
                            + username
                            + "\",\"password\":\""
                            + password
                            + "\",\"remember\":true}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.path("data").path("token").asText();
  }

  private String sendRegisterSmsCode(String phone) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"" + phone + "\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("debugCode")
        .asText();
  }
}
