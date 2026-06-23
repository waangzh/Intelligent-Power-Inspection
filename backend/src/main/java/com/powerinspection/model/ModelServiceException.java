package com.powerinspection.model;

public class ModelServiceException extends RuntimeException {
  public ModelServiceException(String message) {
    super(message);
  }

  public ModelServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
