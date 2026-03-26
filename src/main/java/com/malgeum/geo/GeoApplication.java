package com.malgeum.geo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class GeoApplication {
	public static void main(String[] args) {
		SpringApplication.run(GeoApplication.class, args);
	}

}
