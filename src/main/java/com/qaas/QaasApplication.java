package com.qaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QaasApplication {
    public static void main(String[] args) {
        SpringApplication.run(QaasApplication.class, args);
    }
}
