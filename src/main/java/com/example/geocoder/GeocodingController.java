package com.example.geocoder;

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
        if (address.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        GeocodingResult result = service.geocode(address);
        return result != null
                ? ResponseEntity.ok(result)
                : ResponseEntity.notFound().build();
    }
}