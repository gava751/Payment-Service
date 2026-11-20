package com.innoboard.paymentservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.innoboard.paymentservice.exception.RandomNumberServiceUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomNumberClientTest {

    private static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private RandomNumberClient client;

    @BeforeAll
    static void startWireMock() {
        WIRE_MOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void setUp() {
        WIRE_MOCK.resetAll();
        client = new RandomNumberClient(new RandomNumberClientConfig()
                .randomNumberRestClient(WIRE_MOCK.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    private void stubNumber(String body) {
        WIRE_MOCK.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200).withBody(body)));
    }

    @Test
    void parsesEvenNumberFromResponseBody() {
        stubNumber("42");

        assertThat(client.getRandomNumber()).isEqualTo(42);
    }

    @Test
    void parsesOddNumberWithSurroundingWhitespace() {
        stubNumber(" 7\n");

        assertThat(client.getRandomNumber()).isEqualTo(7);
    }

    @Test
    void apiErrorBecomesServiceUnavailable() {
        WIRE_MOCK.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client.getRandomNumber())
                .isInstanceOf(RandomNumberServiceUnavailableException.class);
    }

    @Test
    void unparseableBodyBecomesServiceUnavailable() {
        stubNumber("not-a-number");

        assertThatThrownBy(() -> client.getRandomNumber())
                .isInstanceOf(RandomNumberServiceUnavailableException.class);
    }

    @Test
    void connectionFailureBecomesServiceUnavailable() {
        int port = WIRE_MOCK.port();
        WIRE_MOCK.stop();

        RandomNumberClient deadClient = new RandomNumberClient(new RandomNumberClientConfig()
                .randomNumberRestClient("http://localhost:" + port, Duration.ofSeconds(2), Duration.ofSeconds(5)));

        assertThatThrownBy(deadClient::getRandomNumber)
                .isInstanceOf(RandomNumberServiceUnavailableException.class);
        WIRE_MOCK.start();
    }
}
