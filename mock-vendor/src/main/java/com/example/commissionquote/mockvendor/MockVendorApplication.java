package com.example.commissionquote.mockvendor;

import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockVendorApplication {

	public static void main(String[] args) {
		SpringApplication.run(MockVendorApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	RandomGenerator randomGenerator() {
		return RandomGenerator.getDefault();
	}

}
