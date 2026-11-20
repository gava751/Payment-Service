package com.innoboard.paymentservice.service;

import com.innoboard.paymentservice.domain.PaymentStatus;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request);

    List<PaymentResponse> getPayments(String userId, String orderId, PaymentStatus status);

    BigDecimal sumByUser(String userId, Instant from, Instant to);

    BigDecimal sumAllUsers(Instant from, Instant to);
}
