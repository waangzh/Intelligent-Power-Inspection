package com.powerinspection.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {
  /**
   * off: register without SMS.
   * mock: local codes (debugCode in response).
   * pnvs: Aliyun 号码认证短信认证（个人可免资质，使用控制台赠送签名/模板）.
   */
  private String mode = "mock";
  private String accessKeyId = "";
  private String accessKeySecret = "";
  /** Console gift signature, e.g. 恒创联众. */
  private String signName = "";
  /** Console gift template for register, e.g. 100001. */
  private String templateCode = "";
  /** Console gift template for reset password, e.g. 100003. */
  private String resetTemplateCode = "";
  private String templateParamName = "code";
  /** Minutes variable in gift templates, usually {@code min}. */
  private String templateMinParamName = "min";
  private String schemeName = "";
  private String endpoint = "dypnsapi.aliyuncs.com";
  private int codeLength = 6;
  private long codeTtlSeconds = 300;
  private long resendIntervalSeconds = 30;
  private int dailyLimitPerPhone = 10;

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public SmsMode resolvedMode() {
    return SmsMode.from(mode);
  }

  public boolean verificationRequired() {
    return resolvedMode() != SmsMode.OFF;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getAccessKeySecret() {
    return accessKeySecret;
  }

  public void setAccessKeySecret(String accessKeySecret) {
    this.accessKeySecret = accessKeySecret;
  }

  public String getSignName() {
    return signName;
  }

  public void setSignName(String signName) {
    this.signName = signName;
  }

  public String getTemplateCode() {
    return templateCode;
  }

  public void setTemplateCode(String templateCode) {
    this.templateCode = templateCode;
  }

  public String getResetTemplateCode() {
    return resetTemplateCode;
  }

  public void setResetTemplateCode(String resetTemplateCode) {
    this.resetTemplateCode = resetTemplateCode;
  }

  public String templateCodeFor(SmsPurpose purpose) {
    if (purpose == SmsPurpose.RESET_PASSWORD) {
      return isBlank(resetTemplateCode) ? templateCode : resetTemplateCode;
    }
    return templateCode;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public String getTemplateParamName() {
    return templateParamName;
  }

  public void setTemplateParamName(String templateParamName) {
    this.templateParamName = templateParamName;
  }

  public String getTemplateMinParamName() {
    return templateMinParamName;
  }

  public void setTemplateMinParamName(String templateMinParamName) {
    this.templateMinParamName = templateMinParamName;
  }

  public String getSchemeName() {
    return schemeName;
  }

  public void setSchemeName(String schemeName) {
    this.schemeName = schemeName;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public int getCodeLength() {
    return codeLength;
  }

  public void setCodeLength(int codeLength) {
    this.codeLength = codeLength;
  }

  public long getCodeTtlSeconds() {
    return codeTtlSeconds;
  }

  public void setCodeTtlSeconds(long codeTtlSeconds) {
    this.codeTtlSeconds = codeTtlSeconds;
  }

  public long getResendIntervalSeconds() {
    return resendIntervalSeconds;
  }

  public void setResendIntervalSeconds(long resendIntervalSeconds) {
    this.resendIntervalSeconds = resendIntervalSeconds;
  }

  public int getDailyLimitPerPhone() {
    return dailyLimitPerPhone;
  }

  public void setDailyLimitPerPhone(int dailyLimitPerPhone) {
    this.dailyLimitPerPhone = dailyLimitPerPhone;
  }

  public int templateMinMinutes() {
    return Math.max(1, (int) Math.ceil(codeTtlSeconds / 60.0));
  }
}
