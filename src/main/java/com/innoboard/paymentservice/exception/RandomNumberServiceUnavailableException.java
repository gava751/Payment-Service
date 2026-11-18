package com.innoboard.paymentservice.exception;

public class RandomNumberServiceUnavailableException extends RuntimeException {

    public RandomNumberServiceUnavailableException() {
        super("Random number service is unavailable");
    }
}
