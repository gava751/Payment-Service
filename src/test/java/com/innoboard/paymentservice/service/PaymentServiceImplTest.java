package com.innoboard.paymentservice.service;

import com.innoboard.paymentservice.client.RandomNumberClient;
import com.innoboard.paymentservice.client.UserClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;
import com.innoboard.paymentservice.dto.UserInfoResponse;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final UserInfoResponse USER = new UserInfoResponse(
            1L, "Ivan", "Ivanov", LocalDate.parse("1990-05-10"), "ivan@example.com", true);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private RandomNumberClient randomNumberClient;

    @Mock
    private UserClient userClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, paymentMapper, randomNumberClient,
                userClient, kafkaTemplate, new ObjectMapper(), PAYMENT_EVENTS_TOPIC);
    }

    private CreatePaymentRequest request() {
        return new CreatePaymentRequest("order-1", "ivan@example.com", new BigDecimal("100.50"));
    }

    private PaymentEntity entity(CreatePaymentRequest request) {
        return PaymentEntity.builder()
                .orderId(request.orderId())
                .paymentAmount(request.paymentAmount())
                .build();
    }

    private void stubResolvedUser() {
        when(userClient.getUserByEmail("ivan@example.com")).thenReturn(USER);
    }

    private void stubResponses() {
        when(paymentMapper.toResponse(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            return new PaymentResponse(entity.getId(), entity.getOrderId(), entity.getUserId(),
                    entity.getStatus(), entity.getTimestamp(), entity.getPaymentAmount());
        });
    }

    private void stubProducer() {
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void createPaymentResolvesUserIdFromUserClientAndSetsSuccessStatus() {
        CreatePaymentRequest request = request();
        stubResolvedUser();
        when(randomNumberClient.getRandomNumber()).thenReturn(42);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubProducer();
        stubResponses();

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.userId()).isEqualTo("1");
        assertThat(response.timestamp()).isNotNull();
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void createPaymentWithOddNumberSetsFailedStatus() {
        CreatePaymentRequest request = request();
        stubResolvedUser();
        when(randomNumberClient.getRandomNumber()).thenReturn(7);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubProducer();
        stubResponses();

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void createPaymentPublishesEventWithResolvedUserIdAndOrderIdKey() {
        CreatePaymentRequest request = request();
        stubResolvedUser();
        when(randomNumberClient.getRandomNumber()).thenReturn(42);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubProducer();

        paymentService.createPayment(request);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(PAYMENT_EVENTS_TOPIC), eq("order-1"), payloadCaptor.capture());
        CreatePaymentEvent event = new ObjectMapper().readValue(
                payloadCaptor.getValue(), CreatePaymentEvent.class);
        assertThat(event.orderId()).isEqualTo("order-1");
        assertThat(event.userId()).isEqualTo("1");
        assertThat(event.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.paymentAmount()).isEqualByComparingTo("100.50");
    }

    @Test
    void createPaymentDoesNotFailWhenEventSendFails() {
        CreatePaymentRequest request = request();
        stubResolvedUser();
        when(randomNumberClient.getRandomNumber()).thenReturn(42);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(anyString(), anyString(), any(String.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        stubResponses();

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(kafkaTemplate).send(eq(PAYMENT_EVENTS_TOPIC), anyString(), anyString());
    }

    @Test
    void getPaymentsDelegatesToRepositoryAndMapsResponses() {
        PaymentEntity entity = entity(request());
        entity.setUserId("1");
        entity.setStatus(PaymentStatus.SUCCESS);
        entity.setTimestamp(Instant.now());
        when(paymentRepository.findPayments("1", "order-1", PaymentStatus.SUCCESS)).thenReturn(List.of(entity));
        stubResponses();

        List<PaymentResponse> result = paymentService.getPayments("1", "order-1", PaymentStatus.SUCCESS);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().orderId()).isEqualTo("order-1");
        assertThat(result.getFirst().status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void getPaymentsWithoutMatchesReturnsEmptyList() {
        when(paymentRepository.findPayments("user-x", null, null)).thenReturn(List.of());

        List<PaymentResponse> result = paymentService.getPayments("user-x", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void sumByUserDelegatesToRepository() {
        when(paymentRepository.sumByUserIdAndTimestampBetween("1", FROM, TO))
                .thenReturn(new BigDecimal("300.00"));

        BigDecimal sum = paymentService.sumByUser("1", FROM, TO);

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
        assertThatThrownBy(() -> paymentService.sumByUser("1", TO, FROM))
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
