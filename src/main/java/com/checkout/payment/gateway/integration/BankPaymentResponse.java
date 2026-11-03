package com.checkout.payment.gateway.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BankPaymentResponse(Boolean authorized) {

}
