package com.innoboard.paymentservice.integration;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PaymentLiquibaseIntegrationTest {

    private static final List<String> EXPECTED_INDEX_NAMES = List.of(
            "idx_payments_user_id",
            "idx_payments_order_id",
            "idx_payments_status",
            "idx_payments_user_id_timestamp",
            "idx_payments_timestamp"
    );

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("payment_service"));
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void liquibaseCreatesPaymentsCollection() {
        List<String> collections = mongoTemplate.getDb().listCollectionNames().into(new ArrayList<>());

        assertThat(collections).contains("payments");
    }

    @Test
    void liquibaseCreatesAllPaymentsIndexes() {
        List<String> indexNames = mongoTemplate.getDb().getCollection("payments")
                .listIndexes()
                .into(new ArrayList<>())
                .stream()
                .map(Document.class::cast)
                .map(index -> index.getString("name"))
                .toList();

        assertThat(indexNames).containsAll(EXPECTED_INDEX_NAMES);
    }
}
