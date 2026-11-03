package com.checkout.payment.gateway.configuration;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApplicationConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictNumbers() {
    return builder -> builder.postConfigurer(mapper -> {
      mapper.coercionConfigFor(LogicalType.Integer)
          .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
          .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
      mapper.coercionConfigFor(LogicalType.Textual)
          .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
          .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
          .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    });
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofMillis(10000))
        .setReadTimeout(Duration.ofMillis(10000))
        .build();
  }
}
