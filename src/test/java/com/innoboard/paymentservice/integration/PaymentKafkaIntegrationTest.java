package com.innoboard.paymentservice.integration;

import com.innoboard.paymentservice.client.RandomNumberClient;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.service.PaymentService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PaymentKafkaIntegrationTest extends IntegrationTestBase {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    private RandomNumberClient randomNumberClient;

    @Autowired
    private PaymentService paymentService;

    @Test
    void createPaymentPublishesCreatePaymentEventWithStatus() {
        when(randomNumberClient.getRandomNumber()).thenReturn(42);

        paymentService.createPayment(
                new CreatePaymentRequest("order-kafka-1", "user-kafka@example.com", new BigDecimal("99.99")));

        List<String> events = consumeEvents(PAYMENT_EVENTS_TOPIC, 1, Duration.ofSeconds(45));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst())
                .contains("\"orderId\":\"order-kafka-1\"")
                .contains("\"userId\":\"1\"")
                .contains("\"status\":\"SUCCESS\"");
    }

    private List<String> consumeEvents(String topic, int expectedCount, Duration maxWait) {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("group.id", "it-consumer-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());

        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + maxWait.toMillis();
            while (System.currentTimeMillis() < deadline && values.size() < expectedCount) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500)).records(topic)) {
                    values.add(record.value());
                }
            }
        }
        return values;
    }
}
