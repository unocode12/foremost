package com.unocode.creational.singleton;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SingletonConfig {

    @Bean
    public String hello() {
        return "hello";
    }
}
