package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.integration.BankClient;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {
  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private final PaymentsRepository paymentsRepository;
  private final BankClient bankClient;
  private final PaymentRequestValidator validator;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      BankClient bankClient, PaymentRequestValidator validator) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
    this.validator = validator;
  }

  public GetPaymentResponse getPaymentById(UUID id) {
    PostPaymentResponse payment = paymentsRepository.get(id)
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));
    return new GetPaymentResponse(payment.id(), payment.status(), payment.cardNumberLastFour(),
        payment.expiryMonth(), payment.expiryYear(), payment.currency(), payment.amount());
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request) {
    validator.validate(request);
    boolean authorized = bankClient.authorize(request);
    PostPaymentResponse payment = preparePayment(request, authorized);
    paymentsRepository.add(payment);
    LOG.info("Payment processed: id={}, status={}", payment.id(), payment.status().getName());
    return payment;
  }

  private PostPaymentResponse preparePayment(PostPaymentRequest request,
      boolean authorized) {
    return new PostPaymentResponse(UUID.randomUUID(),
        authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        request.getCardNumber().substring(request.getCardNumber().length() - 4),
        request.getExpiryMonth(),
        request.getExpiryYear(),
        request.getCurrency(),
        request.getAmount());
  }
}
