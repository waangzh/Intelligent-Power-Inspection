package com.powerinspection.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.powerinspection.common.ApiException;
import com.powerinspection.security.TokenService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class RefreshTokenServiceTests {
  @Autowired RefreshTokenService refreshTokenService;

  @Autowired RefreshTokenRepository refreshTokenRepository;

  @Autowired UserRepository userRepository;

  @Autowired TokenService tokenService;

  private UserEntity admin;

  @BeforeEach
  void setUp() {
    refreshTokenRepository.deleteAll();
    admin = userRepository.findByUsername("admin").orElseThrow();
  }

  @Test
  void ordinaryRotationPreservesLastPasswordAuthenticationTime() {
    long oldAuthTime = 1_700_000_000L;
    RefreshTokenService.IssuedRefresh issued = refreshTokenService.issue(admin, true, oldAuthTime);

    RefreshTokenService.RotatedSession rotated = refreshTokenService.rotate(issued.rawToken());

    assertThat(rotated.authTime()).isEqualTo(oldAuthTime);
    assertThat(rotated.refresh().entity().getAuthTimeEpochSeconds()).isEqualTo(oldAuthTime);
    String refreshedAccessToken = tokenService.create(rotated.user(), rotated.authTime());
    assertThatThrownBy(() -> tokenService.requireRecentAuth(refreshedAccessToken))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(403));
  }

  @Test
  void reauthenticationRotationUpdatesPasswordAuthenticationTime() {
    RefreshTokenService.IssuedRefresh issued =
        refreshTokenService.issue(admin, true, 1_700_000_000L);
    long recentAuthTime = 1_800_000_000L;

    RefreshTokenService.RotatedSession rotated =
        refreshTokenService.rotateAfterReauthentication(issued.rawToken(), recentAuthTime);

    assertThat(rotated.authTime()).isEqualTo(recentAuthTime);
    assertThat(rotated.refresh().entity().getAuthTimeEpochSeconds()).isEqualTo(recentAuthTime);
  }

  @Test
  void concurrentRotationAllowsOnlyOneSuccess() throws Exception {
    RefreshTokenService.IssuedRefresh issued =
        refreshTokenService.issue(admin, true, 1_700_000_000L);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<String> rotation =
        () -> {
          ready.countDown();
          start.await(5, TimeUnit.SECONDS);
          try {
            refreshTokenService.rotate(issued.rawToken());
            return "SUCCEEDED";
          } catch (ApiException ex) {
            return "HTTP_" + ex.status().value();
          }
        };

    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<String> first = workers.submit(rotation);
      Future<String> second = workers.submit(rotation);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SUCCEEDED", "HTTP_401");
    } finally {
      workers.shutdownNow();
    }
  }
}
