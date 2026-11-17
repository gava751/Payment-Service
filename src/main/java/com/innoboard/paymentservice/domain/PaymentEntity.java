package com.innoboard.paymentservice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity {

    @Id
    private String id;

    @Field("order_id")
    private String orderId;

    @Field("user_id")
    private String userId;

    @Field("status")
    private PaymentStatus status;

    @Field("timestamp")
    private Instant timestamp;

    @Field("payment_amount")
    private BigDecimal paymentAmount;
}
