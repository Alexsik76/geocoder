package com.example.geocoder;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleGeocodingClient {

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    private final RestClient restClient;
    private final String apiKey;

    public GoogleGeocodingClient(RestClient restClient,
            @Value("${google.geocoding.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public Optional<Coordinates> geocode(String address) {
        GeocodingResponse response = restClient.get()
                .uri(BASE_URL, uri -> uri
                        .queryParam("address", address)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(GeocodingResponse.class);

        if (response == null || !"OK".equals(response.status()) || response.results().isEmpty()) {
            return Optional.empty();
        }

        GeocodingResponse.Location location = response.results().get(0).geometry().location();
        return Optional.of(new Coordinates(location.lat(), location.lng()));
    }

    record GeocodingResponse(String status, List<Result> results) {
        record Result(Geometry geometry) {
        }

        record Geometry(Location location) {
        }

        record Location(double lat, double lng) {
        }
    }
}