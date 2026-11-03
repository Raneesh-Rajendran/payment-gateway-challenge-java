package com.checkout.payment.gateway.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BankPaymentRequestTest {

  @Test
  void stringRepresentationDoesNotExposeCardNumberOrCvv() {
    BankPaymentRequest request = new BankPaymentRequest(
        "1234567890123456", "04/2027", "GBP", 100, "987");

    String representation = request.toString();
    assertAll(
        () -> assertEquals(
            "BankPaymentRequest[cardNumber=****3456, expiryDate=04/2027, currency=GBP, amount=100, cvv=REDACTED]",
            representation),
        () -> assertFalse(representation.contains(request.cardNumber())),
        () -> assertFalse(representation.contains(request.cvv())));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"1", "1234"})
  void stringRepresentationRedactsMissingOrShortCardNumbers(String cardNumber) {
    BankPaymentRequest request = new BankPaymentRequest(
        cardNumber, "04/2027", "GBP", 100, "987");

    assertEquals(
        "BankPaymentRequest[cardNumber=REDACTED, expiryDate=04/2027, currency=GBP, amount=100, cvv=REDACTED]",
        request.toString());
  }
}
