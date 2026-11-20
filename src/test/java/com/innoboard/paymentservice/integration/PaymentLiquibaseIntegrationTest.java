package com.innoboard.paymentservice.integration;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentLiquibaseIntegrationTest extends IntegrationTestBase {

    private static final List<String> EXPECTED_INDEX_NAMES = List.of(
            "idx_payments_user_id",
            "idx_payments_order_id",
            "idx_payments_status",
            "idx_payments_user_id_timestamp",
            "idx_payments_timestamp"
    );

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
