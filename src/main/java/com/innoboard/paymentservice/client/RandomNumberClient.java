package com.innoboard.paymentservice.client;

import com.innoboard.paymentservice.exception.RandomNumberServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class RandomNumberClient {

    private final RestClient randomNumberRestClient;

    public RandomNumberClient(RestClient randomNumberRestClient) {
        this.randomNumberRestClient = randomNumberRestClient;
    }

    public int getRandomNumber() {
        try {
            String body = randomNumberRestClient.get()
                    .retrieve()
                    .body(String.class);
            return Integer.parseInt(body.trim());
        } catch (RestClientException e) {
            log.warn("Random number service call failed: {}", e.getMessage());
            throw new RandomNumberServiceUnavailableException();
        } catch (NumberFormatException e) {
            log.warn("Random number service returned an unexpected value");
            throw new RandomNumberServiceUnavailableException();
        }
    }
}
