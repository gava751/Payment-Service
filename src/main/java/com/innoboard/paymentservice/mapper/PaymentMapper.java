package com.innoboard.paymentservice.mapper;

import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.dto.CreatePaymentRequest;
import com.innoboard.paymentservice.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    PaymentEntity toEntity(CreatePaymentRequest request);

    PaymentResponse toResponse(PaymentEntity entity);
}
