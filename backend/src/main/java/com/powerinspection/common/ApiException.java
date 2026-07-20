package com.powerinspection.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final int code;

  public ApiException(HttpStatus status, int code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, 400, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, 401, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, 403, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, 404, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, 409, message);
  }

  public static ApiException serviceUnavailable(String message) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, 503, message);
  }

  public HttpStatus status() {
    return status;
  }

  public int code() {
    return code;
  }
}
