package com.innoboard.paymentservice.repository;

import com.innoboard.paymentservice.domain.PaymentEntity;
import com.innoboard.paymentservice.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

    private static final String USER_ID_FIELD = "user_id";
    private static final String ORDER_ID_FIELD = "order_id";
    private static final String STATUS_FIELD = "status";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String AMOUNT_FIELD = "payment_amount";
    private static final String TOTAL_FIELD = "total";

    private final MongoTemplate mongoTemplate;

    public PaymentRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<PaymentEntity> findPayments(String userId, String orderId, PaymentStatus status) {
        Criteria criteria = new Criteria();
        if (userId != null) {
            criteria.and(USER_ID_FIELD).is(userId);
        }
        if (orderId != null) {
            criteria.and(ORDER_ID_FIELD).is(orderId);
        }
        if (status != null) {
            criteria.and(STATUS_FIELD).is(status);
        }
        return mongoTemplate.find(new Query(criteria).with(Sort.by(Sort.Direction.ASC, TIMESTAMP_FIELD)), PaymentEntity.class);
    }

    @Override
    public BigDecimal sumByUserIdAndTimestampBetween(String userId, Instant from, Instant to) {
        return executeSum(new Criteria(USER_ID_FIELD).is(userId)
                .andOperator(Criteria.where(TIMESTAMP_FIELD).gte(from), Criteria.where(TIMESTAMP_FIELD).lte(to)));
    }

    @Override
    public BigDecimal sumByTimestampBetween(Instant from, Instant to) {
        return executeSum(Criteria.where(TIMESTAMP_FIELD).gte(from).lte(to));
    }

    private BigDecimal executeSum(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().sum(AMOUNT_FIELD).as(TOTAL_FIELD));
        return mongoTemplate.aggregate(aggregation, PaymentEntity.class, SumResult.class)
                .getMappedResults()
                .stream()
                .findFirst()
                .map(SumResult::total)
                .orElse(BigDecimal.ZERO);
    }

    record SumResult(BigDecimal total) {
    }
}
