package com.innoboard.paymentservice.integration;

import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRepositoryIntegrationTest extends IntegrationTestBase {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");

    private PaymentEntity payment(String userId, String orderId, PaymentStatus status, Instant timestamp, String amount) {
        return PaymentEntity.builder()
                .userId(userId)
                .orderId(orderId)
                .status(status)
                .timestamp(timestamp)
                .paymentAmount(new BigDecimal(amount))
                .build();
    }

    private void seed() {
        paymentRepository.saveAll(List.of(
                payment("user-1", "order-1", PaymentStatus.SUCCESS, Instant.parse("2026-01-05T10:00:00Z"), "100.00"),
                payment("user-1", "order-2", PaymentStatus.FAILED, Instant.parse("2026-01-15T10:00:00Z"), "200.00"),
                payment("user-1", "order-3", PaymentStatus.SUCCESS, Instant.parse("2026-03-01T10:00:00Z"), "300.00"),
                payment("user-2", "order-4", PaymentStatus.SUCCESS, FROM, "50.00"),
                payment("user-2", "order-5", PaymentStatus.FAILED, TO, "75.00")
        ));
    }

    @Test
    void createAssignsGeneratedIdAndUsesSchemaFieldNames() {
        PaymentEntity saved = paymentRepository.save(
                payment("user-1", "order-1", PaymentStatus.SUCCESS, Instant.parse("2026-01-05T10:00:00Z"), "100.50"));

        assertThat(saved.getId()).isNotBlank();

        PaymentEntity loaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getOrderId()).isEqualTo("order-1");
        assertThat(loaded.getUserId()).isEqualTo("user-1");
        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(loaded.getTimestamp()).isEqualTo(Instant.parse("2026-01-05T10:00:00Z"));
        assertThat(loaded.getPaymentAmount()).isEqualByComparingTo("100.50");

        org.bson.Document raw = mongoTemplate.getDb().getCollection("payments").find().first();
        assertThat(raw).containsKeys("order_id", "user_id", "status", "timestamp", "payment_amount");
    }

    @Test
    void findPaymentsFiltersByUserIdOnly() {
        seed();

        List<PaymentEntity> result = paymentRepository.findPayments("user-1", null, null);

        assertThat(result).hasSize(3)
                .allSatisfy(p -> assertThat(p.getUserId()).isEqualTo("user-1"));
    }

    @Test
    void findPaymentsFiltersByOrderIdOnly() {
        seed();

        List<PaymentEntity> result = paymentRepository.findPayments(null, "order-2", null);

        assertThat(result).hasSize(1)
                .first()
                .satisfies(p -> {
                    assertThat(p.getOrderId()).isEqualTo("order-2");
                    assertThat(p.getPaymentAmount()).isEqualByComparingTo("200.00");
                });
    }

    @Test
    void findPaymentsFiltersByStatusOnly() {
        seed();

        List<PaymentEntity> result = paymentRepository.findPayments(null, null, PaymentStatus.FAILED);

        assertThat(result).hasSize(2)
                .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void findPaymentsCombinesUserIdAndStatus() {
        seed();

        List<PaymentEntity> result = paymentRepository.findPayments("user-1", null, PaymentStatus.SUCCESS);

        assertThat(result).hasSize(2)
                .allSatisfy(p -> {
                    assertThat(p.getUserId()).isEqualTo("user-1");
                    assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                });
    }

    @Test
    void findPaymentsCombinesAllFilters() {
        seed();

        List<PaymentEntity> result = paymentRepository.findPayments("user-2", "order-4", PaymentStatus.SUCCESS);

        assertThat(result).hasSize(1)
                .first()
                .satisfies(p -> assertThat(p.getPaymentAmount()).isEqualByComparingTo("50.00"));
    }

    @Test
    void findPaymentsWithoutFiltersReturnsAllSortedByTimestamp() {
        seed();

        List<PaymentEntity> result = paymentRepository.findPayments(null, null, null);

        assertThat(result).hasSize(5);
        assertThat(result).isSortedAccordingTo(Comparator.comparing(PaymentEntity::getTimestamp));
    }

    @Test
    void sumByUserIncludesRangeBoundariesAndExcludesOutsidePayments() {
        seed();

        BigDecimal sum = paymentRepository.sumByUserIdAndTimestampBetween("user-1", FROM, TO);

        assertThat(sum).isEqualByComparingTo("300.00");
    }

    @Test
    void sumForAllUsersAggregatesEveryUserInRange() {
        seed();

        BigDecimal sum = paymentRepository.sumByTimestampBetween(FROM, TO);

        assertThat(sum).isEqualByComparingTo("425.00");
    }

    @Test
    void sumsReturnZeroWhenRangeHasNoPayments() {
        seed();

        BigDecimal userSum = paymentRepository.sumByUserIdAndTimestampBetween("user-1",
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2027-02-01T00:00:00Z"));
        BigDecimal allSum = paymentRepository.sumByTimestampBetween(
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2027-02-01T00:00:00Z"));

        assertThat(userSum).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(allSum).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
