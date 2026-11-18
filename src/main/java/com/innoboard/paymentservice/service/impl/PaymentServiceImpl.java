package com.innoboard.paymentservice.service.impl;

import com.innoboard.paymentservice.client.RandomNumberClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;
import com.innoboard.paymentservice.event.CreatePaymentEvent;
import com.innoboard.paymentservice.mapper.PaymentMapper;
import com.innoboard.paymentservice.repository.PaymentRepository;
import com.innoboard.paymentservice.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomNumberClient randomNumberClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String paymentEventsTopic;

    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentMapper paymentMapper,
                              RandomNumberClient randomNumberClient,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              @Value("${app.kafka.topics.payment-events}") String paymentEventsTopic) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.randomNumberClient = randomNumberClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentEventsTopic = paymentEventsTopic;
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        int randomNumber = randomNumberClient.getRandomNumber();
        PaymentEntity payment = paymentMapper.toEntity(request);
        payment.setStatus(resolveStatus(randomNumber));
        payment.setTimestamp(Instant.now());
        paymentRepository.save(payment);
        publishCreatePaymentEvent(payment);
        return paymentMapper.toResponse(payment);
    }

    @Override
    public List<PaymentResponse> getPayments(String userId, String orderId, PaymentStatus status) {
        return paymentRepository.findPayments(userId, orderId, status).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Override
    public BigDecimal sumByUser(String userId, Instant from, Instant to) {
        validateRange(from, to);
        return paymentRepository.sumByUserIdAndTimestampBetween(userId, from, to);
    }

    @Override
    public BigDecimal sumAllUsers(Instant from, Instant to) {
        validateRange(from, to);
        return paymentRepository.sumByTimestampBetween(from, to);
    }

    private void publishCreatePaymentEvent(PaymentEntity payment) {
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
                        log.info("CreatePaymentEvent sent for payment {} to order {}",
                                event.paymentId(), event.orderId());
                    }
                });
    }

    private PaymentStatus resolveStatus(int randomNumber) {
        return randomNumber % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
    }

    private void validateRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
    }
}
