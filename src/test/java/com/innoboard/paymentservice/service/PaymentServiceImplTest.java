package com.innoboard.paymentservice.service;

import com.innoboard.paymentservice.client.RandomNumberClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;
import com.innoboard.paymentservice.event.CreatePaymentEvent;
import com.innoboard.paymentservice.mapper.PaymentMapper;
import com.innoboard.paymentservice.repository.PaymentRepository;
import com.innoboard.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private RandomNumberClient randomNumberClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, paymentMapper, randomNumberClient,
                kafkaTemplate, new tools.jackson.databind.ObjectMapper(), PAYMENT_EVENTS_TOPIC);
    }

    private CreatePaymentRequest request() {
        return new CreatePaymentRequest("order-1", "user-1", new BigDecimal("100.50"));
    }

    private PaymentEntity entity(CreatePaymentRequest request) {
        return PaymentEntity.builder()
                .orderId(request.orderId())
                .userId(request.userId())
                .paymentAmount(request.paymentAmount())
                .build();
    }

    private void stubResponses() {
        when(paymentMapper.toResponse(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            return new PaymentResponse(entity.getId(), entity.getOrderId(), entity.getUserId(),
                    entity.getStatus(), entity.getTimestamp(), entity.getPaymentAmount());
        });
    }

    @Test
    void createPaymentWithEvenNumberSetsSuccessStatusAndPublishesEvent() {
        CreatePaymentRequest request = request();
        when(randomNumberClient.getRandomNumber()).thenReturn(42);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        stubResponses();

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.timestamp()).isNotNull();
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void createPaymentWithOddNumberSetsFailedStatus() {
        CreatePaymentRequest request = request();
        when(randomNumberClient.getRandomNumber()).thenReturn(7);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        stubResponses();

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void createPaymentPublishesEventWithOrderIdKeyAndStatus() {
        CreatePaymentRequest request = request();
        when(randomNumberClient.getRandomNumber()).thenReturn(42);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        paymentService.createPayment(request);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(PAYMENT_EVENTS_TOPIC), eq("order-1"), payloadCaptor.capture());
        CreatePaymentEvent event = new tools.jackson.databind.ObjectMapper().readValue(
                payloadCaptor.getValue(), CreatePaymentEvent.class);
        assertThat(event.orderId()).isEqualTo("order-1");
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.paymentAmount()).isEqualByComparingTo("100.50");
    }

    @Test
    void getPaymentsDelegatesToRepositoryAndMapsResponses() {
        PaymentEntity entity = entity(request());
        entity.setStatus(PaymentStatus.SUCCESS);
        entity.setTimestamp(Instant.now());
        when(paymentRepository.findPayments("user-1", "order-1", PaymentStatus.SUCCESS)).thenReturn(List.of(entity));
        stubResponses();

        List<PaymentResponse> result = paymentService.getPayments("user-1", "order-1", PaymentStatus.SUCCESS);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().orderId()).isEqualTo("order-1");
        assertThat(result.getFirst().status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void sumByUserDelegatesToRepository() {
        when(paymentRepository.sumByUserIdAndTimestampBetween("user-1", FROM, TO))
                .thenReturn(new BigDecimal("300.00"));

        BigDecimal sum = paymentService.sumByUser("user-1", FROM, TO);

        assertThat(sum).isEqualByComparingTo("300.00");
    }

    @Test
    void sumAllUsersDelegatesToRepository() {
        when(paymentRepository.sumByTimestampBetween(FROM, TO)).thenReturn(new BigDecimal("425.00"));

        BigDecimal sum = paymentService.sumAllUsers(FROM, TO);

        assertThat(sum).isEqualByComparingTo("425.00");
    }

    @Test
    void sumByUserRejectsReversedRange() {
        assertThatThrownBy(() -> paymentService.sumByUser("user-1", TO, FROM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void sumAllUsersRejectsReversedRange() {
        assertThatThrownBy(() -> paymentService.sumAllUsers(TO, FROM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verifyNoInteractions(paymentRepository);
    }
}
