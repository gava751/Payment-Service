package com.innoboard.paymentservice.event;

import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentEventPublisher(kafkaTemplate, new ObjectMapper(), PAYMENT_EVENTS_TOPIC);
    }

    private PaymentEntity payment(PaymentStatus status) {
        return PaymentEntity.builder()
                .id("pay-1")
                .orderId("order-1")
                .userId("1")
                .status(status)
                .timestamp(Instant.parse("2026-11-21T12:00:00Z"))
                .paymentAmount(new BigDecimal("100.50"))
                .build();
    }

    @Test
    void publishesJsonEventKeyedByOrderId() {
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(payment(PaymentStatus.IN_PROGRESS));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(eq(PAYMENT_EVENTS_TOPIC), eq("order-1"), payloadCaptor.capture());
        CreatePaymentEvent event = new ObjectMapper().readValue(payloadCaptor.getValue(), CreatePaymentEvent.class);
        assertThat(event.paymentId()).isEqualTo("pay-1");
        assertThat(event.orderId()).isEqualTo("order-1");
        assertThat(event.userId()).isEqualTo("1");
        assertThat(event.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-11-21T12:00:00Z"));
        assertThat(event.paymentAmount()).isEqualByComparingTo("100.50");
    }

    @Test
    void sendFailureDoesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatCode(() -> publisher.publish(payment(PaymentStatus.SUCCESS)))
                .doesNotThrowAnyException();
    }
}
