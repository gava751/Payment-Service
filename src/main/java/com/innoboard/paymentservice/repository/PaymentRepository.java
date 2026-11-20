package com.innoboard.paymentservice.repository;

import com.innoboard.paymentservice.domain.PaymentEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<PaymentEntity, String>, PaymentRepositoryCustom {
}
