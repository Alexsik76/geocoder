package com.example.geocoder;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

@Service
public class GeocodingService {

    private final GeoLocationRepository repository;
    private final GoogleGeocodingClient googleClient;

    public GeocodingService(GeoLocationRepository repository, GoogleGeocodingClient googleClient) {
        this.repository = repository;
        this.googleClient = googleClient;
    }

    @Cacheable(value = "geocoding", unless = "#result == null")
    public Optional<GeocodingResult> geocode(String rawAddress) {
        String address = normalize(rawAddress);

        Optional<GeoLocation> fromDb = repository.findByAddress(address);
        if (fromDb.isPresent()) {
            GeoLocation location = fromDb.get();
            return Optional.of(new GeocodingResult(
                    location.getAddress(), location.getLatitude(), location.getLongitude(), "database"));
        }

        Optional<Coordinates> fromGoogle = googleClient.geocode(address);
        if (fromGoogle.isEmpty()) {
            return Optional.empty();
        }

        Coordinates coordinates = fromGoogle.get();
        GeoLocation saved = repository.save(
                new GeoLocation(address, coordinates.latitude(), coordinates.longitude()));
        return Optional.of(new GeocodingResult(
                saved.getAddress(), saved.getLatitude(), saved.getLongitude(), "google"));
    }

    private String normalize(String address) {
        return address == null ? "" : address.trim();
    }
}