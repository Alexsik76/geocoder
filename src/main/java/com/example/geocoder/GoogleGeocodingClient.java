package com.example.geocoder;

import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GoogleGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleGeocodingClient.class);

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    private final RestClient restClient;
    private final String apiKey;

    public GoogleGeocodingClient(RestClient restClient,
            @Value("${google.geocoding.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Retry(name = "google", fallbackMethod = "geocodeFallback")
    public Optional<Coordinates> geocode(String address) {
        GeocodingResponse response;
        try {
            response = restClient.get()
                    .uri(BASE_URL, uri -> uri
                            .queryParam("address", address)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(GeocodingResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Google Geocoding API call failed for address '{}': {}: {}",
                    address, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }

        if (response == null || !"OK".equals(response.status()) || response.results().isEmpty()) {
            return Optional.empty();
        }

        GeocodingResponse.Location location = response.results().get(0).geometry().location();
        return Optional.of(new Coordinates(location.lat(), location.lng()));
    }

    // Invoked reflectively by the resilience4j retry proxy (see fallbackMethod above)
    // once all retry attempts for a ResourceAccessException (connect/read timeout) are
    // exhausted, so a transient Google outage still degrades to "address not resolved"
    // instead of a 500. Not called directly, hence the "unused" warning suppression.
    @SuppressWarnings("unused")
    private Optional<Coordinates> geocodeFallback(String address, ResourceAccessException e) {
        log.warn("Google Geocoding API unreachable for address '{}' after retries: {}: {}",
                address, e.getClass().getSimpleName(), e.getMessage());
        return Optional.empty();
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