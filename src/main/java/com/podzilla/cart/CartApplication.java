package com.podzilla.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.podzilla.cart.repository")
@ComponentScan(basePackages = { "com.podzilla" })
public class CartApplication {
    public static void main(final String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
