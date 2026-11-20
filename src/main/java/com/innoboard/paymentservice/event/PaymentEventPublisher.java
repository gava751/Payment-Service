package com.innoboard.paymentservice.event;

import com.innoboard.paymentservice.domain.PaymentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String paymentEventsTopic;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                 @Value("${app.kafka.topics.payment-events}") String paymentEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    public void publish(PaymentEntity payment) {
        CreatePaymentEvent event = new CreatePaymentEvent(
                payment.getId(), payment.getOrderId(), payment.getUserId(),
                payment.getStatus(), payment.getTimestamp(), payment.getPaymentAmount());
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(paymentEventsTopic, event.orderId(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send CreatePaymentEvent for payment {} to order {}: {}",
                                event.paymentId(), event.orderId(), ex.getMessage());
                    } else {
                        log.info("CreatePaymentEvent sent for payment {} to order {} with status {}",
                                event.paymentId(), event.orderId(), event.status());
                    }
                });
    }
}
