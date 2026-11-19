package com.innoboard.paymentservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentApiIntegrationTest extends IntegrationTestBase {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    static final WireMockServer RANDOM_API = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        KAFKA.start();
        RANDOM_API.start();
    }

    @DynamicPropertySource
    static void externalDependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.services.random-number-url", RANDOM_API::baseUrl);
    }

    @BeforeEach
    void resetRandomApi() {
        RANDOM_API.resetAll();
    }

    private void stubNumber(String body) {
        stubFailure(200, body);
    }

    private void stubFailure(int status, String body) {
        RANDOM_API.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(status).withBody(body)));
    }

    @Test
    void createPaymentReturns201WithRandomEvenStatusAndSavesDocument() throws Exception {
        stubNumber("42");

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-1","userEmail":"user-it@example.com","paymentAmount":149.99}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.orderId").value("order-it-1"))
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.paymentAmount").value(149.99));

        List<PaymentEntity> stored = paymentRepository.findPayments("1", null, null);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(stored.getFirst().getPaymentAmount()).isEqualByComparingTo("149.99");
    }

    @Test
    void createPaymentPublishesEventToKafkaTopic() throws Exception {
        stubNumber("7");

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-2","userEmail":"user-it@example.com","paymentAmount":10.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        List<String> events = consumeEvents();
        assertThat(events).anySatisfy(event -> {
            assertThat(event).contains("\"orderId\":\"order-it-2\"");
            assertThat(event).contains("\"status\":\"FAILED\"");
        });
    }

    @Test
    void createPaymentReturns503WhenRandomApiIsDown() throws Exception {
        stubFailure(500, "boom");

        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-3","userEmail":"user-it@example.com","paymentAmount":5.00}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Random number service is unavailable"));

        assertThat(paymentRepository.findPayments("1", null, null)).isEmpty();
    }

    @Test
    void createPaymentRejectsBlankOrderId() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"","userEmail":"user-it@example.com","paymentAmount":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orderId").isNotEmpty());

        assertThat(paymentRepository.findPayments(null, null, null)).isEmpty();
    }

    @Test
    void createPaymentRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-1","userEmail":"not-an-email","paymentAmount":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userEmail").isNotEmpty());

        assertThat(paymentRepository.findPayments(null, null, null)).isEmpty();
    }

    @Test
    void createPaymentRejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-1","userEmail":"user-it@example.com","paymentAmount":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentAmount").isNotEmpty());

        assertThat(paymentRepository.findPayments(null, null, null)).isEmpty();
    }

    @Test
    void createPaymentReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-1","userEmail":"unknown@example.com","paymentAmount":10}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found in User Service"));

        assertThat(paymentRepository.findPayments(null, null, null)).isEmpty();
    }

    @Test
    void createPaymentReturns503WhenUserServiceDown() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("""
                                {"orderId":"order-it-1","userEmail":"down@example.com","paymentAmount":10}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("User Service is unavailable"));

        assertThat(paymentRepository.findPayments(null, null, null)).isEmpty();
    }

    @Test
    void createPaymentRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void createPaymentRejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType("text/plain")
                        .content("plain body"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void getAllPaymentsFiltersByUserIdAndStatus() throws Exception {
        paymentRepository.saveAll(List.of(
                payment("user-filter", "order-a", PaymentStatus.SUCCESS, "100.00"),
                payment("user-filter", "order-b", PaymentStatus.FAILED, "200.00"),
                payment("user-other", "order-c", PaymentStatus.SUCCESS, "300.00")));

        mockMvc.perform(get("/api/payments").param("userId", "user-filter").param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value("order-a"));

        mockMvc.perform(get("/api/payments").param("userId", "user-filter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllPaymentsRejectsUnknownStatusParameter() throws Exception {
        mockMvc.perform(get("/api/payments").param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));
    }

    @Test
    void sumByUserReturnsTotalInRange() throws Exception {
        paymentRepository.saveAll(List.of(
                payment("user-sum", "order-a", PaymentStatus.SUCCESS, Instant.parse("2026-03-01T00:00:00Z"), "100.00"),
                payment("user-sum", "order-b", PaymentStatus.FAILED, Instant.parse("2026-03-02T00:00:00Z"), "50.00"),
                payment("user-sum", "order-c", PaymentStatus.SUCCESS, Instant.parse("2026-09-01T00:00:00Z"), "999.00")));

        mockMvc.perform(get("/api/payments/sum")
                        .param("userId", "user-sum")
                        .param("from", "2026-03-01T00:00:00Z")
                        .param("to", "2026-03-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(150.00));
    }

    @Test
    void sumAllUsersReturnsTotalAcrossUsers() throws Exception {
        paymentRepository.saveAll(List.of(
                payment("user-1", "order-a", PaymentStatus.SUCCESS, Instant.parse("2026-03-01T00:00:00Z"), "100.00"),
                payment("user-2", "order-b", PaymentStatus.SUCCESS, Instant.parse("2026-03-02T00:00:00Z"), "75.50")));

        mockMvc.perform(get("/api/payments/sum/all")
                        .param("from", "2026-03-01T00:00:00Z")
                        .param("to", "2026-03-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(175.50));
    }

    @Test
    void sumsRejectReversedRange() throws Exception {
        mockMvc.perform(get("/api/payments/sum")
                        .param("userId", "user-1")
                        .param("from", "2026-03-31T00:00:00Z")
                        .param("to", "2026-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));

        mockMvc.perform(get("/api/payments/sum/all")
                        .param("from", "2026-03-31T00:00:00Z")
                        .param("to", "2026-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
    }

    private PaymentEntity payment(String userId, String orderId, PaymentStatus status, String amount) {
        return payment(userId, orderId, status, Instant.now(), amount);
    }

    private PaymentEntity payment(String userId, String orderId, PaymentStatus status, Instant timestamp, String amount) {
        return PaymentEntity.builder()
                .userId(userId)
                .orderId(orderId)
                .status(status)
                .timestamp(timestamp)
                .paymentAmount(new BigDecimal(amount))
                .build();
    }

    private List<String> consumeEvents() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("group.id", "it-api-consumer-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());

        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(PAYMENT_EVENTS_TOPIC));
            Supplier<Boolean> found = () -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500)).records(PAYMENT_EVENTS_TOPIC)) {
                    values.add(record.value());
                }
                return values.stream().anyMatch(event -> event.contains("\"orderId\":\"order-it-2\""));
            };
            await()
                    .atMost(Duration.ofSeconds(45))
                    .pollInterval(Duration.ofMillis(500))
                    .until(found::get);
        }
        return values;
    }
}
