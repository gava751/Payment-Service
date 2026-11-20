package com.innoboard.paymentservice.repository;

import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentRepositoryCustom {

    List<PaymentEntity> findPayments(String userId, String orderId, PaymentStatus status);

    BigDecimal sumByUserIdAndTimestampBetween(String userId, Instant from, Instant to);

    BigDecimal sumByTimestampBetween(Instant from, Instant to);
}
