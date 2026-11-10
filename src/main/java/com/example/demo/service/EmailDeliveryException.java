package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmailDeliveryException extends ResponseStatusException {
    public EmailDeliveryException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is temporarily unavailable");
    }
}
