package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);
  private static final String INVALID_REQUEST_MESSAGE = "Invalid payment request";

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> notFound() {
    LOG.warn("Payment lookup failed: payment not found, status=404");
    return ResponseEntity.status(404).body(new ErrorResponse("Page not found"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException ex) {
    LOG.warn("Payment request rejected: field value validation failed, status=400");
    return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidFields(MethodArgumentNotValidException ex) {
    LOG.debug("Payment request rejected: model validation failed, status=400");
    String message = ex.getBindingResult().getAllErrors().stream()
        .map(ObjectError::getDefaultMessage)
        .filter(java.util.Objects::nonNull)
        .findFirst().orElse(INVALID_REQUEST_MESSAGE);
    return ResponseEntity.badRequest().body(new ErrorResponse(message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException ex) {
    LOG.debug("Payment request rejected: malformed JSON or incompatible field type, status=400");
    if (ex.getCause() instanceof MismatchedInputException mapping && !mapping.getPath().isEmpty()) {
      String field = mapping.getPath().get(0).getFieldName();
      Class<?> type = mapping.getTargetType();
      if (field != null && type != null) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(field + " must be of type " + type.getSimpleName()));
      }
    }
    return ResponseEntity.badRequest().body(new ErrorResponse(INVALID_REQUEST_MESSAGE));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidId() {
    LOG.warn("Payment lookup rejected: invalid identifier, status=400");
    return ResponseEntity.badRequest().body(new ErrorResponse("Invalid payment identifier"));
  }

  @ExceptionHandler(RestClientResponseException.class)
  public ResponseEntity<ErrorResponse> dependencyHttpException(RestClientResponseException ex) {
    HttpStatus status = switch (ex.getStatusCode().value()) {
      case 400 -> HttpStatus.UNPROCESSABLE_ENTITY;
      case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
      default -> HttpStatus.BAD_GATEWAY;
    };
    if (ex.getStatusCode().value() == 503) {
      LOG.warn("Acquiring bank unavailable: status=503, exceptionType={}",
          ex.getClass().getSimpleName());
    } else {
      LOG.error("Invalid response received from acquiring bank: status={}, exceptionType={}",
          ex.getStatusCode().value(), ex.getClass().getSimpleName());
    }
    return ResponseEntity.status(status).body(new ErrorResponse(status.getReasonPhrase()));
  }

  @ExceptionHandler(RestClientException.class)
  public ResponseEntity<ErrorResponse> dependencyClientException(RestClientException ex) {
    if (ex instanceof ResourceAccessException) {
      LOG.warn("Acquiring bank communication failed: exceptionType={}, causeType={}",
          ex.getClass().getSimpleName(),
          ex.getCause() == null ? "none" : ex.getCause().getClass().getSimpleName());
    } else {
      LOG.error("Invalid response received from acquiring bank: exceptionType={}",
          ex.getClass().getSimpleName());
    }
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorResponse(HttpStatus.BAD_GATEWAY.getReasonPhrase()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> responseStatusException(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(ex.getReason()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception ex) {
    LOG.error("Unexpected payment failure, status=500", ex);
    return ResponseEntity.internalServerError().body(new ErrorResponse("Internal server error"));
  }
}
