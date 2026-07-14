package com.powerinspection.robot;

/** Bridge sync 的已知结果分类；不携带请求头、Token 或内部 URL。 */
public class RobotBridgeDeploymentException extends RuntimeException {
  public enum Disposition { UNKNOWN, FAILED }

  private final Disposition disposition;
  private final String errorCode;

  public RobotBridgeDeploymentException(Disposition disposition, String errorCode, String message) {
    super(message);
    this.disposition = disposition;
    this.errorCode = errorCode;
  }

  public Disposition getDisposition() { return disposition; }
  public String getErrorCode() { return errorCode; }

  public static RobotBridgeDeploymentException unknown(String code, String message) {
    return new RobotBridgeDeploymentException(Disposition.UNKNOWN, code, message);
  }

  public static RobotBridgeDeploymentException failed(String code, String message) {
    return new RobotBridgeDeploymentException(Disposition.FAILED, code, message);
  }
}
