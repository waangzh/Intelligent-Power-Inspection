package com.powerinspection.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockSmsSender {
  private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

  public void sendVerificationCode(String phone, String code) {
    log.info("[mock-sms] verification code for {} => {}", phone, code);
  }
}
