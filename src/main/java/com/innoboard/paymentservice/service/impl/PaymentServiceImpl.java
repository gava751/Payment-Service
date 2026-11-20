package com.innoboard.paymentservice.service.impl;

import com.innoboard.paymentservice.client.UserClient;
import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;
import com.innoboard.paymentservice.dto.UserInfoResponse;
import com.innoboard.paymentservice.event.PaymentEventPublisher;
import com.innoboard.paymentservice.mapper.PaymentMapper;
import com.innoboard.paymentservice.repository.PaymentRepository;
import com.innoboard.paymentservice.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final UserClient userClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentFinalizationProcessor finalizationProcessor;

    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentMapper paymentMapper,
                              UserClient userClient, PaymentEventPublisher paymentEventPublisher,
                              PaymentFinalizationProcessor finalizationProcessor) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.userClient = userClient;
        this.paymentEventPublisher = paymentEventPublisher;
        this.finalizationProcessor = finalizationProcessor;
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        UserInfoResponse user = userClient.getUserByEmail(request.userEmail());
        PaymentEntity payment = paymentMapper.toEntity(request);
        payment.setUserId(String.valueOf(user.id()));
        payment.setStatus(PaymentStatus.IN_PROGRESS);
        payment.setTimestamp(Instant.now());
        paymentRepository.save(payment);
        paymentEventPublisher.publish(payment);
        finalizationProcessor.finalizePayment(payment.getId());
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

    private void validateRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
    }
}
