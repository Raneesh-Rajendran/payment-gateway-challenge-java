package com.checkout.payment.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.SocketTimeoutException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private RestTemplate restTemplate;
  @Autowired
  private ObjectMapper mapper;
  @Autowired
  private PaymentsRepository paymentsRepository;
  private MockRestServiceServer bankClient;

  @BeforeEach
  void setUp() {
    bankClient = MockRestServiceServer.createServer(restTemplate);
  }

  @AfterEach
  void verifyBankCalls() {
    bankClient.verify();
  }

  private String request() {
    return """
        {"card_number":"0000000000000001","expiry_month":4,"expiry_year":9999,
         "currency":"GBP","amount":100,"cvv":"012"}
        """;
  }

  @Test
  void authorizesPaymentAndRetrievesStoredDetails() throws Exception {
    processAndRetrievePayment(true);
  }

  @Test
  void declinesPaymentAndRetrievesStoredDetails() throws Exception {
    processAndRetrievePayment(false);
  }

  private void processAndRetrievePayment(boolean authorized) throws Exception {
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            MockRestRequestMatchers.content().json("""
                {"card_number":"0000000000000001","expiry_date":"04/9999",
                 "currency":"GBP","amount":100,"cvv":"012"}
                """, true))
        .andRespond(withSuccess("{\"authorized\":" + authorized
            + ",\"authorization_code\":\"bankClient-secret-code\"}", MediaType.APPLICATION_JSON));
    String response = mvc.perform(
            post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(authorized ? "Authorized" : "Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0001"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(9999))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$", aMapWithSize(7)))
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    String id = mapper.readTree(response).get("id").asText();
    UUID paymentId = assertDoesNotThrow(() -> UUID.fromString(id));
    var storedPayment = paymentsRepository.get(paymentId).orElseThrow();
    assertEquals(mapper.readTree(response), mapper.valueToTree(storedPayment));
    mvc.perform(get("/payment/" + id)).andExpect(status().isOk())
        .andExpect(content().json(response, true));
  }

  @Test
  void rejectsInvalidCardWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("card_number", mapper.readTree("\"1234567890123x\""));
    assertRejected(body.toString(), "Card number must contain 14 to 19 digits");
  }

  @Test
  void rejectsInvalidExpiryMonthWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("expiry_month", mapper.readTree("13"));
    assertRejected(body.toString(), "Expiry month must be between 1 and 12");
  }

  @Test
  void rejectsExpiredCardWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("expiry_year", mapper.readTree("2000"));
    assertRejected(body.toString(), "Card has expired");
  }

  @Test
  void rejectsUnsupportedCurrencyWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("currency", mapper.readTree("\"JPY\""));
    assertRejected(body.toString(), "Currency must be GBP, USD or EUR");
  }

  @Test
  void rejectsNonPositiveAmountWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("amount", mapper.readTree("0"));
    assertRejected(body.toString(), "Amount must be a positive integer");
  }

  @Test
  void rejectsFractionalAmountWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("amount", mapper.readTree("1.5"));
    assertRejected(body.toString(), "amount must be of type Integer");
  }

  @Test
  void rejectsInvalidCvvWithoutCallingBank() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set("cvv", mapper.readTree("\"12x\""));
    assertRejected(body.toString(), "CVV must contain 3 or 4 digits");
  }

  private void assertRejected(String body, String message) throws Exception {
    // With no bank expectations, any attempted bank request fails the test.
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(1)))
        .andExpect(jsonPath("$.message").value(message));
  }

  @Test
  void handlesBank503WithoutRetrying() throws Exception {
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("sensitive bank details"));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Service Unavailable"))
        .andExpect(jsonPath("$", aMapWithSize(1)));
  }

  @Test
  void handlesBankTimeoutWithoutRetrying() throws Exception {
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withException(new SocketTimeoutException("sensitive timeout details")));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bad Gateway"))
        .andExpect(jsonPath("$", aMapWithSize(1)));
  }


  @ParameterizedTest
  @ValueSource(strings = {"{}", "invalid-json"})
  void handlesInvalidBankResponse(String response) throws Exception {
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().isBadGateway());
  }

  @ParameterizedTest
  @ValueSource(ints = {13, 20})
  void rejectsCardLengthsOutsideBoundaries(int length) throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.put("card_number", "1".repeat(length));
    assertRejected(body.toString(), "Card number must contain 14 to 19 digits");
  }

  @ParameterizedTest
  @CsvSource({"card_number, 11111111111111", "card_number, 1111111111111111111",
      "cvv, 0123", "currency, USD", "currency, EUR", "amount, 2147483647"})
  void acceptsValidBoundaryValuesAndForwardsThemToBank(String field, String value)
      throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    if (field.equals("amount")) {
      body.put(field, Integer.parseInt(value));
    } else {
      body.put(field, value);
    }
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(MockRestRequestMatchers.content().json(
            "{\"" + field + "\":" + mapper.writeValueAsString(body.get(field)) + "}"))
        .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));
    String responseField = field.equals("card_number") ? "cardNumberLastFour" : field;
    Object expected = field.equals("amount") ? Integer.parseInt(value)
        : field.equals("card_number") ? "1111" : value;
    var result = mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON)
            .content(body.toString()))
        .andExpect(status().isCreated());
    if (field.equals("cvv")) {
      result.andExpect(jsonPath("$.cvv").doesNotExist());
    } else {
      result.andExpect(jsonPath("$." + responseField).value(expected));
    }
  }

  @ParameterizedTest
  @CsvSource({"amount, 2147483648", "amount, -2147483649", "amount, '\"100\"'",
      "expiry_month, '\"4\"'", "expiry_year, '\"9999\"'",
      "card_number, 12345678901234", "cvv, 123", "currency, 123"})
  void rejectsOverflowAndScalarCoercionWithoutCallingBank(String field, String jsonValue)
      throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.set(field, mapper.readTree(jsonValue));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(1)))
        .andExpect(jsonPath("$.message").isString());
  }

  @ParameterizedTest
  @CsvSource({"card_number, Card number is required", "expiry_month, Expiry month is required",
      "expiry_year, Expiry year is required", "currency, Currency is required",
      "amount, Amount is required", "cvv, CVV is required"})
  void rejectsMissingAndNullFieldsWithoutCallingBank(String field, String message)
      throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(request());
    body.remove(field);
    assertRejected(body.toString(), message);
    body.putNull(field);
    assertRejected(body.toString(), message);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", "12345678-1234-1234-1234-12345678901z"})
  void rejectsMalformedRetrievalIds(String id) throws Exception {
    mvc.perform(get("/payment/" + id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", aMapWithSize(1)))
        .andExpect(jsonPath("$.message").value("Invalid payment identifier"));
  }

  @ParameterizedTest
  @CsvSource({"400, 422, Unprocessable Entity", "401, 502, Bad Gateway",
      "429, 502, Bad Gateway", "500, 502, Bad Gateway", "503, 503, Service Unavailable"})
  void mapsBankErrorsWithoutLeakingResponseDetails(int bankStatus, int gatewayStatus,
      String message, CapturedOutput output) throws Exception {
    String sensitiveDetails = "card_number=1234567890123456,cvv=987,sensitive-bank-body";
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withStatus(HttpStatus.valueOf(bankStatus)).body(sensitiveDetails));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().is(gatewayStatus))
        .andExpect(jsonPath("$", aMapWithSize(1)))
        .andExpect(jsonPath("$.message").value(message));
    assertThat(output.getAll()).contains("status=" + bankStatus, "exceptionType=")
        .doesNotContain(sensitiveDetails, "1234567890123456", "cvv=987");
  }

  @Test
  void doesNotLogSensitiveCommunicationFailureDetails(CapturedOutput output) throws Exception {
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(
            withException(new SocketTimeoutException("sensitive-timeout-card-1234567890123456")));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().isBadGateway());
    assertThat(output.getAll()).contains("exceptionType=ResourceAccessException")
        .doesNotContain("sensitive-timeout-card-1234567890123456");
  }

  @Test
  void doesNotLogSensitiveMalformedBankResponse(CapturedOutput output) throws Exception {
    bankClient.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess("{\"authorized\":\"sensitive-bank-value-1234567890123456\"}",
            MediaType.APPLICATION_JSON));
    mvc.perform(post("/payment").contentType(MediaType.APPLICATION_JSON).content(request()))
        .andExpect(status().isBadGateway());
    assertThat(output.getAll()).contains("exceptionType=")
        .doesNotContain("sensitive-bank-value-1234567890123456");
  }

  @Test
  void returns404ForUnknownPaymentId() throws Exception {
    mvc.perform(get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"))
        .andExpect(jsonPath("$.status").doesNotExist());
  }
}
