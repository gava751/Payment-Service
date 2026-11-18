package com.innoboard.paymentservice.event;

import com.innoboard.paymentservice.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePaymentEvent(
        String paymentId,
        String orderId,
        String userId,
        PaymentStatus status,
        Instant timestamp,
        BigDecimal paymentAmount
) {
}
