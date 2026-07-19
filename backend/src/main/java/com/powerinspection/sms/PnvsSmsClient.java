package com.powerinspection.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aliyun Phone Number Verification Service (号码认证) SMS auth API.
 */
@Component
public class PnvsSmsClient {
  private static final Logger log = LoggerFactory.getLogger(PnvsSmsClient.class);

  private final SmsProperties properties;
  private final ObjectMapper objectMapper;
  private volatile Client client;

  public PnvsSmsClient(SmsProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public void sendVerifyCode(String phone) {
    sendVerifyCode(phone, properties.getTemplateCode());
  }

  public void sendVerifyCode(String phone, String templateCode) {
    ensureConfigured();
    String resolvedTemplate = isBlank(templateCode) ? properties.getTemplateCode() : templateCode;
    if (isBlank(resolvedTemplate)) {
      throw ApiException.badRequest("号码认证未配置短信模板");
    }
    try {
      Map<String, String> params = new LinkedHashMap<>();
      params.put(properties.getTemplateParamName(), "##code##");
      params.put(properties.getTemplateMinParamName(), String.valueOf(properties.templateMinMinutes()));

      SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
        .setPhoneNumber(phone)
        .setSignName(properties.getSignName())
        .setTemplateCode(resolvedTemplate)
        .setTemplateParam(objectMapper.writeValueAsString(params))
        .setCodeType(1L)
        .setCodeLength((long) properties.getCodeLength())
        .setValidTime(properties.getCodeTtlSeconds())
        .setInterval(properties.getResendIntervalSeconds())
        .setDuplicatePolicy(1L)
        .setReturnVerifyCode(false)
        .setCountryCode("86");
      if (!isBlank(properties.getSchemeName())) {
        request.setSchemeName(properties.getSchemeName());
      }

      SendSmsVerifyCodeResponse response = client().sendSmsVerifyCode(request);
      String code = response.getBody() == null ? null : response.getBody().getCode();
      String message = response.getBody() == null ? null : response.getBody().getMessage();
      Boolean success = response.getBody() == null ? null : response.getBody().getSuccess();
      if (!Boolean.TRUE.equals(success) && !"OK".equalsIgnoreCase(code)) {
        log.warn("PNVS send rejected phone={} code={} message={}", phone, code, message);
        throw ApiException.badRequest(mapError(code, message));
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("PNVS send failed for {}", phone, ex);
      throw ApiException.badRequest("短信发送失败，请稍后重试");
    }
  }

  public void checkVerifyCode(String phone, String verifyCode) {
    ensureConfigured();
    try {
      CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
        .setPhoneNumber(phone)
        .setVerifyCode(verifyCode)
        .setCountryCode("86")
        .setCaseAuthPolicy(1L);
      if (!isBlank(properties.getSchemeName())) {
        request.setSchemeName(properties.getSchemeName());
      }

      CheckSmsVerifyCodeResponse response = client().checkSmsVerifyCode(request);
      String apiCode = response.getBody() == null ? null : response.getBody().getCode();
      String message = response.getBody() == null ? null : response.getBody().getMessage();
      Boolean success = response.getBody() == null ? null : response.getBody().getSuccess();
      String verifyResult = response.getBody() == null || response.getBody().getModel() == null
        ? null
        : response.getBody().getModel().getVerifyResult();

      if (!Boolean.TRUE.equals(success) && !"OK".equalsIgnoreCase(apiCode)) {
        log.warn("PNVS check API failed phone={} code={} message={}", phone, apiCode, message);
        throw ApiException.badRequest(mapError(apiCode, message));
      }
      if (!"PASS".equalsIgnoreCase(verifyResult)) {
        throw ApiException.badRequest("验证码错误或已失效");
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("PNVS check failed for {}", phone, ex);
      throw ApiException.badRequest("验证码校验失败，请稍后重试");
    }
  }

  private void ensureConfigured() {
    if (isBlank(properties.getAccessKeyId())
        || isBlank(properties.getAccessKeySecret())
        || isBlank(properties.getSignName())
        || isBlank(properties.getTemplateCode())) {
      throw ApiException.badRequest("号码认证未配置完整（AccessKey / 赠送签名 / 赠送模板），请联系管理员");
    }
  }

  private Client client() throws Exception {
    Client existing = client;
    if (existing != null) {
      return existing;
    }
    synchronized (this) {
      if (client == null) {
        Config config = new Config()
          .setAccessKeyId(properties.getAccessKeyId())
          .setAccessKeySecret(properties.getAccessKeySecret());
        config.endpoint = properties.getEndpoint();
        client = new Client(config);
      }
      return client;
    }
  }

  private static String mapError(String code, String message) {
    if (code == null) {
      return message == null || message.isBlank() ? "短信发送失败，请稍后重试" : message;
    }
    return switch (code) {
      case "BUSINESS_LIMIT_CONTROL", "FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL" -> "发送过于频繁，请稍后再试";
      case "MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_NUMBER_ILLEGAL" -> "手机号格式不正确";
      case "FUNCTION_NOT_OPENED" -> "未开通号码认证短信认证功能";
      case "INVALID_PARAMETERS" -> "短信参数不正确，请检查签名与模板配置";
      default -> message == null || message.isBlank() ? "短信服务调用失败：" + code : message;
    };
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
