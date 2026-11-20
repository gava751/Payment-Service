package com.innoboard.paymentservice.service;

import com.innoboard.paymentservice.client.UserClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;
import com.innoboard.paymentservice.dto.UserInfoResponse;
import com.innoboard.paymentservice.event.PaymentEventPublisher;
import com.innoboard.paymentservice.mapper.PaymentMapper;
import com.innoboard.paymentservice.repository.PaymentRepository;
import com.innoboard.paymentservice.service.impl.PaymentFinalizationProcessor;
import com.innoboard.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");
    private static final UserInfoResponse USER = new UserInfoResponse(
            1L, "Ivan", "Ivanov", LocalDate.parse("1990-05-10"), "ivan@example.com", true);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private UserClient userClient;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentFinalizationProcessor finalizationProcessor;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, paymentMapper, userClient,
                paymentEventPublisher, finalizationProcessor);
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

    @Test
    void createPaymentResolvesUserSavesInProgressAndTriggersFinalization() {
        CreatePaymentRequest request = request();
        when(userClient.getUserByEmail("ivan@example.com")).thenReturn(USER);
        when(paymentMapper.toEntity(request)).thenReturn(entity(request));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity saved = invocation.getArgument(0);
            saved.setId("pay-1");
            return saved;
        });
        when(paymentMapper.toResponse(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            return new PaymentResponse(entity.getId(), entity.getOrderId(), entity.getUserId(),
                    entity.getStatus(), entity.getTimestamp(), entity.getPaymentAmount());
        });

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response.id()).isEqualTo("pay-1");
        assertThat(response.userId()).isEqualTo("1");
        assertThat(response.status()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(response.timestamp()).isNotNull();

        ArgumentCaptor<PaymentEntity> saveCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        verify(paymentEventPublisher).publish(saveCaptor.getValue());
        verify(finalizationProcessor).finalizePayment("pay-1");
    }

    @Test
    void getPaymentsDelegatesToRepositoryAndMapsResponses() {
        PaymentEntity entity = entity(request());
        entity.setUserId("1");
        entity.setStatus(PaymentStatus.SUCCESS);
        entity.setTimestamp(Instant.now());
        when(paymentRepository.findPayments("1", "order-1", PaymentStatus.SUCCESS)).thenReturn(List.of(entity));
        when(paymentMapper.toResponse(entity)).thenReturn(new PaymentResponse(entity.getId(), entity.getOrderId(),
                entity.getUserId(), entity.getStatus(), entity.getTimestamp(), entity.getPaymentAmount()));

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
