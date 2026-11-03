package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class PostPaymentRequest {

  @JsonProperty("card_number")
  @NotNull(message = "Card number is required")
  private String cardNumber;

  @JsonProperty("expiry_month")
  @NotNull(message = "Expiry month is required")
  private Integer expiryMonth;

  @JsonProperty("expiry_year")
  @NotNull(message = "Expiry year is required")
  private Integer expiryYear;

  @NotNull(message = "Currency is required")
  private String currency;

  @NotNull(message = "Amount is required")
  private Integer amount;

  @NotNull(message = "CVV is required")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }
}
