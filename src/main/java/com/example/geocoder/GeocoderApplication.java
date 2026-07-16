package com.example.geocoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GeocoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeocoderApplication.class, args);
    }

}