package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  @Test
  void requiresExpiryAfterCurrentUtcMonth() {
    PaymentRequestValidator validator = new PaymentRequestValidator(
        Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC));
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("0000000000000001");
    request.setExpiryMonth(12);
    request.setExpiryYear(2026);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("012");

    var exception = assertThrows(IllegalArgumentException.class,
        () -> validator.validate(request));
    assertEquals("Card has expired", exception.getMessage());

    request.setExpiryMonth(1);
    request.setExpiryYear(2027);
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void requiresAllSixFields() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(new PostPaymentRequest());

      assertEquals(6, violations.size());
      assertEquals(Set.of("cardNumber", "expiryMonth", "expiryYear", "currency", "amount", "cvv"),
          violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet()));
      assertEquals(Set.of("Card number is required", "Expiry month is required",
              "Expiry year is required", "Currency is required", "Amount is required",
              "CVV is required"),
          violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet()));
    }
  }
}
