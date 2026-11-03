package com.checkout.payment.gateway.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.NonNull;

public record BankPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String currency,
    int amount,
    String cvv) {

  @Override
  @NonNull
  public String toString() {
    return "BankPaymentRequest[cardNumber=" + (cardNumber != null && cardNumber.length() > 4
        ? "****" + cardNumber.substring(cardNumber.length() - 4)
        : "REDACTED")
        + ", expiryDate=" + expiryDate
        + ", currency=" + currency + ", amount=" + amount + ", cvv=REDACTED]";
  }
}
