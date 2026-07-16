package com.example.geocoder;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;



@Service
public class GeocodingService {

    private final CacheManager cacheManager;

    private GeoLocationRepository repository;
    private GoogleGeocodingClient googleClient;




    public GeocodingService(CacheManager cacheManager, GeoLocationRepository repository, GoogleGeocodingClient googleClient) {
        this.repository = repository;
        this.googleClient = googleClient;
        this.cacheManager = cacheManager;
    }

    public GeocodingResult geocode(String rawAddress) {
        String address = normalize(rawAddress);
        
        GeocodingResult cachedResult = getFromCache(address);
        if (cachedResult != null) {
            return cachedResult;
        }

        Optional<GeoLocation> fromDb = repository.findByAddress(address);
        if (fromDb.isPresent()) {
            GeoLocation location = fromDb.get();
            GeocodingResult result = new GeocodingResult(
                    location.getAddress(), location.getLatitude(), location.getLongitude(), "database");
            putToCache(address, result);
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
        putToCache(address, result);
        return result;
    }

    private GeocodingResult getFromCache(String address) {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("geocoding");
            if (cache != null) {
                Cache.ValueWrapper cached = cache.get(address);
                if (cached != null) {
                    GeocodingResult cachedResult = (GeocodingResult) cached.get();
                    if (cachedResult != null) {
                        return new GeocodingResult(
                                cachedResult.address(),
                                cachedResult.latitude(),
                                cachedResult.longitude(),
                                "cache");
                    }
                }
            }
        }
        return null;
    }

    private void putToCache(String address, GeocodingResult result) {
        if (cacheManager != null && result != null) {
            Cache cache = cacheManager.getCache("geocoding");
            if (cache != null) {
                cache.put(address, result);
            }
        }
    }

    private String normalize(String address) {
        return address == null ? "" : address.trim();
    }
}