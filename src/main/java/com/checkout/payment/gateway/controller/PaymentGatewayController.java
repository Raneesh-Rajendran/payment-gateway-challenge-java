package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Payment processing and retrieval")
@RestController("api")
public class PaymentGatewayController {
  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(summary = "Retrieve a payment", description =
      "Returns a previously authorized or declined payment by its gateway-generated UUID.")
  @ApiResponse(responseCode = "200", description = "Payment found",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = GetPaymentResponse.class)))
  @ApiResponse(responseCode = "400", description = "Invalid payment UUID; returns a message-only error response",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "No stored payment exists for the UUID; returns message: Page not found",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "500", description = "Unexpected internal failure",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/payment/{id}")
  public ResponseEntity<GetPaymentResponse> getPaymentById(
      @Parameter(description = "Payment UUID", required = true)
      @PathVariable UUID id) {
    return ResponseEntity.ok(paymentGatewayService.getPaymentById(id));
  }

  @Operation(summary = "Process a payment",
      description = "Submit a card payment.")
  @ApiResponse(responseCode = "201", description = "Payment created with Authorized or Declined status",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = PostPaymentResponse.class)))
  @ApiResponse(responseCode = "400", description = "Invalid payment request; returns an error message and the bank is not called",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "422", description = "Bank rejected the payment request with HTTP 400; returns a message-only error response",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "502", description = "Invalid or unreadable bank response, unexpected bank HTTP status, connection failure or timeout; returns a message-only error response",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "503", description = "Bank HTTP 503; returns message: Service Unavailable",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "500", description = "Unexpected internal failure",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/payment")
  public ResponseEntity<PostPaymentResponse> processPayment(
      @Valid @RequestBody PostPaymentRequest request) {
    PostPaymentResponse payment = paymentGatewayService.processPayment(request);
    return ResponseEntity.status(201).body(payment);
  }
}
