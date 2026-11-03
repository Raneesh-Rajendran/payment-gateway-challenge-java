package com.checkout.payment.gateway.integration;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);

  private final RestTemplate restTemplate;
  private final String baseUrl;

  public BankClient(RestTemplate restTemplate, @Value("${bank.base-url}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
  }

  public boolean authorize(PostPaymentRequest payment) {

    BankPaymentRequest request = preparePayload(payment);
    LOG.debug("Sending payment to acquiring bank: request={}", request);
    var response = restTemplate.postForEntity(baseUrl + "/payments", request,
        BankPaymentResponse.class);
    BankPaymentResponse body = response.getBody();
    LOG.debug("Acquiring bank response: status={}, body={}", response.getStatusCode().value(),
        body);
    if (response.getStatusCode().value() == 200 && body != null && body.authorized() != null) {
      return body.authorized();
    }
    LOG.error("Invalid response received from acquiring bank: {}", response);
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
        HttpStatus.BAD_GATEWAY.getReasonPhrase());
  }

  private BankPaymentRequest preparePayload(PostPaymentRequest payment) {
    return new BankPaymentRequest(payment.getCardNumber(),
        String.format(Locale.ROOT, "%02d/%04d", payment.getExpiryMonth(), payment.getExpiryYear()),
        payment.getCurrency(), payment.getAmount(), payment.getCvv());
  }
}
