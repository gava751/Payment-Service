package com.innoboard.paymentservice.service.impl;

import com.innoboard.paymentservice.client.RandomNumberClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.event.PaymentEventPublisher;
import com.innoboard.paymentservice.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentFinalizationProcessor {

    private final PaymentRepository paymentRepository;
    private final RandomNumberClient randomNumberClient;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentFinalizationProcessor(PaymentRepository paymentRepository,
                                        RandomNumberClient randomNumberClient,
                                        PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.randomNumberClient = randomNumberClient;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Async
    public void finalizePayment(String paymentId) {
        try {
            paymentRepository.findById(paymentId).ifPresent(this::complete);
        } catch (RuntimeException e) {
            log.warn("Payment {} stays IN_PROGRESS: {}", paymentId, e.getMessage());
        }
    }

    private void complete(PaymentEntity payment) {
        int randomNumber = randomNumberClient.getRandomNumber();
        payment.setStatus(randomNumber % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(payment);
        paymentEventPublisher.publish(payment);
        log.info("Payment {} finalized as {}", payment.getId(), payment.getStatus());
    }
}
