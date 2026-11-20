package com.innoboard.paymentservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 100) String orderId,
        @NotBlank @Email @Size(max = 100) String userEmail,
        @NotNull @Positive BigDecimal paymentAmount
) {
}
