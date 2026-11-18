package com.example.demo.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.demo.service.DemoDataSeedService;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true")
public class DemoDataSeedRunner implements ApplicationRunner {

    private final DemoDataSeedService seedService;

    public DemoDataSeedRunner(DemoDataSeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedService.seed();
    }
}
