package com.example.geocoder;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class GeocodingService {

    @Autowired
    private CacheManager cacheManager;

    private GeoLocationRepository repository;
    private GoogleGeocodingClient googleClient;



    @Autowired
    public GeocodingService(GeoLocationRepository repository, GoogleGeocodingClient googleClient) {
        this.repository = repository;
        this.googleClient = googleClient;
    }

    public GeocodingResult geocode(String rawAddress) {
        String address = normalize(rawAddress);
        // Check cache manually to ensure priority over DB and Google
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("geocoding");
            if (cache != null) {
                Cache.ValueWrapper cached = cache.get(address);
                if (cached != null) {
                    GeocodingResult cachedResult = (GeocodingResult) cached.get();
                    return cachedResult;
                }
            }
        }

        // Existing DB lookup retained for when cache miss
        Optional<GeoLocation> fromDb = repository.findByAddress(address);
        if (fromDb.isPresent()) {
            GeoLocation location = fromDb.get();
            GeocodingResult result = new GeocodingResult(
                    location.getAddress(), location.getLatitude(), location.getLongitude(), "database");
            if (cacheManager != null) {
                Cache cache = cacheManager.getCache("geocoding");
                if (cache != null) {
                    cache.put(address, result);
                }
            }
            return result;
        }

        Optional<Coordinates> fromGoogle = googleClient.geocode(address);
        if (fromGoogle.isEmpty()) {
            return null;
        }

        Coordinates coordinates = fromGoogle.get();
        GeoLocation saved = repository.save(
                new GeoLocation(address, coordinates.latitude(), coordinates.longitude()));
        GeocodingResult result = new GeocodingResult(
                saved.getAddress(), saved.getLatitude(), saved.getLongitude(), "google");
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("geocoding");
            if (cache != null) {
                cache.put(address, result);
            }
        }
        return result;
    }

    private String normalize(String address) {
        return address == null ? "" : address.trim();
    }
}