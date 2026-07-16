package com.example.geocoder;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;

@Service
public class GeocodingService {

    private final CacheManager cacheManager;


    private final GeoLocationRepository repository;
    private final GoogleGeocodingClient googleClient;

    public GeocodingService(GeoLocationRepository repository, GoogleGeocodingClient googleClient, CacheManager cacheManager) {
        this.repository = repository;
        this.googleClient = googleClient;
        this.cacheManager = cacheManager;
    }




    @Cacheable(value = "geocoding", unless = "#result == null")
    public GeocodingResult geocode(String rawAddress) {
        String address = normalize(rawAddress);
        // Check cache manually to ensure priority over DB and Google
        Cache.ValueWrapper cached = cacheManager.getCache("geocoding").get(address);
        if (cached != null) {
            return (GeocodingResult) cached.get();
        }


        // Existing DB lookup retained for when cache miss
        Optional<GeoLocation> fromDb = repository.findByAddress(address);
        if (fromDb.isPresent()) {
            GeoLocation location = fromDb.get();
            return new GeocodingResult(
                    location.getAddress(), location.getLatitude(), location.getLongitude(), "database");
        }

        Optional<Coordinates> fromGoogle = googleClient.geocode(address);
        if (fromGoogle.isEmpty()) {
            return null;
        }

        Coordinates coordinates = fromGoogle.get();
        GeoLocation saved = repository.save(
                new GeoLocation(address, coordinates.latitude(), coordinates.longitude()));
        return new GeocodingResult(
                saved.getAddress(), saved.getLatitude(), saved.getLongitude(), "google");
    }

    private String normalize(String address) {
        return address == null ? "" : address.trim();
    }
}