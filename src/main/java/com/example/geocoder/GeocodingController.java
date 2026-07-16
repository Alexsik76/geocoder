package com.example.geocoder;

import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GeocodingController {

    private final GeocodingService service;

    public GeocodingController(GeocodingService service) {
        this.service = service;
    }

    @GetMapping("/api/geocode")
    public ResponseEntity<GeocodingResult> geocode(@RequestParam String address) {
        Optional<GeocodingResult> result = service.geocode(address);
        return result
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}