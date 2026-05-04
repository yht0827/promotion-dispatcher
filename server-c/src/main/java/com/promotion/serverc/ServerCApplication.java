package com.promotion.serverc;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRabbit
public class ServerCApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerCApplication.class, args);
    }
}
