package com.powerinspection.common;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.status()).body(ApiResponse.error(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
      .map(FieldError::getDefaultMessage)
      .collect(Collectors.joining("；"));
    if (message.isBlank()) {
      message = "参数校验失败";
    }
    return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiResponse<Void>> handleNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "接口不存在"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(405, "请求方法不支持"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
    return ResponseEntity.internalServerError().body(ApiResponse.error(500, "服务器内部错误"));
  }
}
