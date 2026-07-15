package com.powerinspection.robot;

/** 不携带远端响应体、内部 URL 或任何凭据的 Bridge 执行失败。 */
public class RobotBridgeExecutionException extends RuntimeException {
  public enum Disposition { EXPLICIT_FAILURE, UNKNOWN }

  private final Disposition disposition;
  private final String errorCode;

  public RobotBridgeExecutionException(Disposition disposition, String errorCode, String message) {
    super(message);
    this.disposition = disposition;
    this.errorCode = errorCode;
  }

  public Disposition getDisposition() { return disposition; }
  public String getErrorCode() { return errorCode; }

  public static RobotBridgeExecutionException explicit(String code, String message) {
    return new RobotBridgeExecutionException(Disposition.EXPLICIT_FAILURE, code, message);
  }

  public static RobotBridgeExecutionException unknown(String code, String message) {
    return new RobotBridgeExecutionException(Disposition.UNKNOWN, code, message);
  }
}
