package com.innoboard.paymentservice.service;

import com.innoboard.paymentservice.client.RandomNumberClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.event.PaymentEventPublisher;
import com.innoboard.paymentservice.exception.RandomNumberServiceUnavailableException;
import com.innoboard.paymentservice.repository.PaymentRepository;
import com.innoboard.paymentservice.service.impl.PaymentFinalizationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFinalizationProcessorTest {

    private static final String PAYMENT_ID = "pay-1";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RandomNumberClient randomNumberClient;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    private PaymentFinalizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PaymentFinalizationProcessor(paymentRepository, randomNumberClient, paymentEventPublisher);
    }

    private PaymentEntity inProgressPayment() {
        return PaymentEntity.builder()
                .id(PAYMENT_ID)
                .orderId("order-1")
                .userId("1")
                .status(PaymentStatus.IN_PROGRESS)
                .timestamp(Instant.now())
                .paymentAmount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void evenNumberFinalizesPaymentAsSuccessAndPublishesEvent() {
        PaymentEntity payment = inProgressPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(randomNumberClient.getRandomNumber()).thenReturn(42);

        processor.finalizePayment(PAYMENT_ID);

        ArgumentCaptor<PaymentEntity> saveCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentEventPublisher).publish(saveCaptor.getValue());
    }

    @Test
    void oddNumberFinalizesPaymentAsFailedAndPublishesEvent() {
        PaymentEntity payment = inProgressPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(randomNumberClient.getRandomNumber()).thenReturn(7);

        processor.finalizePayment(PAYMENT_ID);

        ArgumentCaptor<PaymentEntity> saveCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentEventPublisher).publish(saveCaptor.getValue());
    }

    @Test
    void randomApiFailureLeavesPaymentInProgress() {
        PaymentEntity payment = inProgressPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(randomNumberClient.getRandomNumber()).thenThrow(new RandomNumberServiceUnavailableException());

        processor.finalizePayment(PAYMENT_ID);

        verifyNoInteractions(paymentEventPublisher);
        verify(paymentRepository).findById(PAYMENT_ID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    void missingPaymentIsIgnored() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        processor.finalizePayment(PAYMENT_ID);

        verifyNoInteractions(randomNumberClient);
        verifyNoInteractions(paymentEventPublisher);
        verify(paymentRepository).findById(PAYMENT_ID);
    }
}
