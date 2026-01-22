package com.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.exam", "Service"})
public class AlgoApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlgoApplication.class, args);
	}

}
