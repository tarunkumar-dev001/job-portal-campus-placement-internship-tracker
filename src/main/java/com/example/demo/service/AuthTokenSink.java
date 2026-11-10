package com.example.demo.service;

import com.example.demo.entity.SecurityToken;

public interface AuthTokenSink {
    void deliver(String email, SecurityToken.Type type, String rawToken);
}
