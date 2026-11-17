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

## Configuration

All settings are provided via environment variables. Local values live in an
ignored `.env` (see `.env.example`):

| Variable         | Default (local)                              | Description                    |
|------------------|----------------------------------------------|--------------------------------|
| `MONGO_URI`      | `mongodb://localhost:27017/payment_service`  | MongoDB connection URI         |
| `SERVER_PORT`    | `8084`                                       | HTTP port                      |
| `MONGO_USER`     | — (no default, required)                     | Mongo user for docker compose  |
| `MONGO_PASSWORD` | — (no default, required)                     | Mongo password for compose     |
| `MONGO_DB`       | — (no default, required)                     | Mongo database for compose     |

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
