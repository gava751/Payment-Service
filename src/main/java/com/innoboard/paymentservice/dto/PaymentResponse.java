package com.innoboard.paymentservice.dto;

import com.innoboard.paymentservice.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String orderId,
        String userId,
        PaymentStatus status,
        Instant timestamp,
        BigDecimal paymentAmount
) {
}
