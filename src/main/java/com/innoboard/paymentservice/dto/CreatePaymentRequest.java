package com.innoboard.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 100) String orderId,
        @NotBlank @Size(max = 100) String userId,
        @NotNull @Positive BigDecimal paymentAmount
) {
}
