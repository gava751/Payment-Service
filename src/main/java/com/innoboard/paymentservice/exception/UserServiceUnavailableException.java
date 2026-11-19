package com.innoboard.paymentservice.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException() {
        super("User Service is unavailable");
    }
}
