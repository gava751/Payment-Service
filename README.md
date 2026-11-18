# Payment Service (InnoBoard)

Spring Boot microservice managing payments for orders.

## Stack

- Java 25 (LTS)
- Spring Boot 4.1.1 (Web MVC, Data MongoDB, Validation, Actuator)
- MongoDB (collection `payments`)
- Liquibase + liquibase-mongodb extension (collection/index migrations)
- Lombok

## Data model (Task: DB structure)

Document collection `payments` with fields:

| Field            | Description                          |
|------------------|--------------------------------------|
| `id`             | document id                          |
| `order_id`       | reference to the related order       |
| `user_id`        | reference to the paying user         |
| `status`         | payment status (SUCCESS / FAILED)    |
| `timestamp`      | payment timestamp                    |
| `payment_amount` | payment amount                       |

The collection and all indexes are created exclusively by Liquibase
(`src/main/resources/db/changelog`).

Indexes (each tied to a known access scenario):

| Index                            | Purpose                                          |
|----------------------------------|--------------------------------------------------|
| `idx_payments_user_id`           | payments of a user                               |
| `idx_payments_order_id`          | payments of an order                             |
| `idx_payments_status`            | payments filtered by status                      |
| `idx_payments_user_id_timestamp` | sum of payments in a date range for one user     |
| `idx_payments_timestamp`         | sum of payments in a date range for all users    |

`user_id` and `order_id` are logical references to other services and have no
database-level references (cross-service boundary).

## DAO Layer (Task: DAO Layer)

`PaymentEntity` (`@Document("payments")`) maps the schema fields
(`order_id`, `user_id`, `status`, `timestamp`, `payment_amount` — snake_case
in the document, camelCase in Java). `id` is a database-generated ObjectId;
amounts are `BigDecimal` (Decimal128), `timestamp` is `Instant`,
`status` is the `PaymentStatus` enum (`SUCCESS` / `FAILED`, stored by name).

`PaymentRepository` extends `MongoRepository` plus a custom fragment:

| Method | Purpose |
|--------|---------|
| `findPayments(userId, orderId, status)` | payments by any combination of user/order/status (nulls skipped), sorted by timestamp |
| `sumByUserIdAndTimestampBetween(userId, from, to)` | total amount for one user in an inclusive date range |
| `sumByTimestampBetween(from, to)` | total amount for all users in an inclusive date range |

The two sum methods use aggregation (`match` + `group` → `$sum`), backed by
the `idx_payments_user_id_timestamp` / `idx_payments_timestamp` indexes.
`sum` returns `0` when the range matches no payments.

## Service Layer (Task: Service Layer)

Controller operates DTOs, the DAO layer operates entities; mapping is done by
MapStruct (`PaymentMapper`).

Endpoints (`/api/payments`):

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/payments` | create payment; status comes from an external random-number API (even → SUCCESS, odd → FAILED), 503 if that API is unavailable |
| GET | `/api/payments?userId=&orderId=&status=` | payments by any combination of user/order/status |
| GET | `/api/payments/sum?userId=&from=&to=` | total for one user in a date range |
| GET | `/api/payments/sum/all?from=&to=` | total for all users in a date range |

`CreatePaymentRequest` carries validation annotations (`@NotBlank` ids,
`@NotNull @Positive` amount); invalid bodies → 400 with field errors.
`timestamp` is set server-side, `id` is database-generated.

## Kafka (Task: Kafka)

Kafka runs in Docker (KRaft, `apache/kafka:4.0.0`). On every created payment
the service publishes a `CREATE_PAYMENT` event as JSON (Boot ObjectMapper,
String payload) to the `payment-events` topic, keyed by `orderId`:

```json
{"paymentId":"...","orderId":"...","userId":"...","status":"SUCCESS",
 "timestamp":"...ISO-8601...","paymentAmount":99.99}
```

Order Service consumes this topic and moves the order status
(`SUCCESS` → `PAID`, `FAILED` → `CANCELLED`, only from `NEW`).



All settings are provided via environment variables. Local values live in an
ignored `.env` (see `.env.example`):

| Variable         | Default (local)                              | Description                    |
|------------------|----------------------------------------------|--------------------------------|
| `MONGO_URI`      | `mongodb://localhost:27017/payment_service`  | MongoDB connection URI         |
| `SERVER_PORT`    | `8084`                                       | HTTP port                      |
| `MONGO_USER`     | — (no default, required)                     | Mongo user for docker compose  |
| `MONGO_PASSWORD` | — (no default, required)                     | Mongo password for compose     |
| `MONGO_DB`       | — (no default, required)                     | Mongo database for compose     |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                    | Kafka brokers                  |
| `PAYMENT_EVENTS_TOPIC` | `payment-events`                        | CREATE_PAYMENT event topic     |
| `RANDOM_NUMBER_URL` | random.org integers endpoint               | External random number API     |
| `RANDOM_NUMBER_CONNECT_TIMEOUT` | `2s`                           | Random API connect timeout     |
| `RANDOM_NUMBER_READ_TIMEOUT` | `5s`                              | Random API read timeout        |

## Running locally

```bash
docker run -d --name payment-service-db -p 27017:27017 mongo:8.0
```

```bash
export JAVA_HOME="C:/Users/gava751/.jdks/temurin-25.0.4.1"
./mvnw spring-boot:run
```

## Running with docker compose

```bash
cp .env.example .env
docker compose up -d --build
```

Liquibase applies the changelogs on app startup (collection `payments` +
indexes).
