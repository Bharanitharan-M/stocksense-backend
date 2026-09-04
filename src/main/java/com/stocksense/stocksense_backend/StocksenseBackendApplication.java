package com.stocksense.stocksense_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StocksenseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(StocksenseBackendApplication.class, args);
	}
}
