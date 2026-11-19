package com.innoboard.paymentservice.integration;

import com.innoboard.paymentservice.repository.PaymentRepository;
import java.io.IOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTestBase {

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    static final MockWebServer USER_SERVICE_MOCK = new MockWebServer();

    static {
        MONGO.start();
        USER_SERVICE_MOCK.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/internal/users/by-email")) {
                    if (path.contains("unknown")) {
                        return new MockResponse().setResponseCode(404);
                    }
                    if (path.contains("down")) {
                        return new MockResponse().setResponseCode(500);
                    }
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {"id":1,"name":"Ivan","surname":"Ivanov","birthDate":"1990-05-10",
                                    "email":"user@example.com","active":true}
                                    """);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        try {
            USER_SERVICE_MOCK.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start User Service mock", e);
        }
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("payment_service"));
        registry.add("app.services.user-url", () -> "http://localhost:" + USER_SERVICE_MOCK.getPort());
        registry.add("app.internal.token", () -> "test-internal-token");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected PaymentRepository paymentRepository;

    @BeforeEach
    void clearPayments() {
        mongoTemplate.remove(new Query(), "payments");
    }
}
