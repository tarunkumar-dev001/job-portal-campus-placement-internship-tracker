package com.example.demo.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.demo.service.AuthTokenSink;

import jakarta.annotation.PostConstruct;

@Component
@Profile("prod")
public class ProductionReadinessValidator {

    private final ObjectProvider<AuthTokenSink> tokenSinks;

    public ProductionReadinessValidator(ObjectProvider<AuthTokenSink> tokenSinks) {
        this.tokenSinks = tokenSinks;
    }

    @PostConstruct
    void requireEmailDelivery() {
        if (tokenSinks.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "Production profile requires an AuthTokenSink email delivery implementation.");
        }
    }
}
