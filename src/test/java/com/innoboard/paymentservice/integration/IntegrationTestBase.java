package com.innoboard.paymentservice.integration;

import com.innoboard.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("payment_service"));
    }

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected PaymentRepository paymentRepository;

    @BeforeEach
    void clearPayments() {
        mongoTemplate.remove(new Query(), "payments");
    }
}
