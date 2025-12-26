package com.unocode.factorymethod;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FactoryMethodConfig {

    @Bean
    public String hello() {
        return "hello";
    }
}
