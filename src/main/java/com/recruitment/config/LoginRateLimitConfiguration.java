package com.recruitment.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class LoginRateLimitConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
