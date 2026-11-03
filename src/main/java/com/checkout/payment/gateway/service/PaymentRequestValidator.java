package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestValidator {

  private final Clock clock;

  public PaymentRequestValidator(Clock clock) {
    this.clock = clock;
  }

  public void validate(PostPaymentRequest request) {
    if (!request.getCardNumber().matches("[0-9]{14,19}")) {
      throw new IllegalArgumentException("Card number must contain 14 to 19 digits");
    }
    if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      throw new IllegalArgumentException("Expiry month must be between 1 and 12");
    }
    if (request.getExpiryYear() < 1000 || request.getExpiryYear() > 9999) {
      throw new IllegalArgumentException("Expiry year must have four digits");
    }
    if (!YearMonth.of(request.getExpiryYear(), request.getExpiryMonth())
        .isAfter(YearMonth.now(clock))) {
      throw new IllegalArgumentException("Card has expired");
    }
    if (!request.getCurrency().matches("GBP|USD|EUR")) {
      throw new IllegalArgumentException("Currency must be GBP, USD or EUR");
    }
    if (request.getAmount() < 1) {
      throw new IllegalArgumentException("Amount must be a positive integer");
    }
    if (!request.getCvv().matches("[0-9]{3,4}")) {
      throw new IllegalArgumentException("CVV must contain 3 or 4 digits");
    }
  }
}
