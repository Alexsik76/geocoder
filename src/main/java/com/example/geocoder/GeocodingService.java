package com.example.geocoder;

import java.util.Optional;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;



@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final CacheManager cacheManager;

    private GeoLocationRepository repository;
    private GoogleGeocodingClient googleClient;




    public GeocodingService(CacheManager cacheManager, GeoLocationRepository repository, GoogleGeocodingClient googleClient) {
        this.repository = repository;
        this.googleClient = googleClient;
        this.cacheManager = cacheManager;
    }

    public GeocodingResult geocode(String rawAddress) {
        String normalizedAddress = normalize(rawAddress);
        
        GeocodingResult cachedResult = getFromCache(normalizedAddress);
        if (cachedResult != null) {
            return cachedResult;
        }

        Optional<GeoLocation> fromDb = repository.findByAddress(normalizedAddress);
        if (fromDb.isPresent()) {
            GeoLocation location = fromDb.get();
            GeocodingResult result = new GeocodingResult(
                    location.getAddress(), location.getLatitude(), location.getLongitude(), "database");
            putToCache(normalizedAddress, result);
            return result;
        }

        String originalTrimmed = rawAddress == null ? "" : rawAddress.trim();
        Optional<Coordinates> fromGoogle = googleClient.geocode(originalTrimmed);
        if (fromGoogle.isEmpty()) {
            return null;
        }

        Coordinates coordinates = fromGoogle.get();
        GeoLocation saved = repository.save(
                new GeoLocation(normalizedAddress, coordinates.latitude(), coordinates.longitude()));
        GeocodingResult result = new GeocodingResult(
                saved.getAddress(), saved.getLatitude(), saved.getLongitude(), "google");
        putToCache(normalizedAddress, result);
        return result;
    }

    private GeocodingResult getFromCache(String address) {
        try {
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
        } catch (RuntimeException e) {
            log.warn("Cache read failed, falling back to database/Google: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            log.debug("Cache read failure details", e);
        }
        return null;
    }

    private void putToCache(String address, GeocodingResult result) {
        try {
            if (cacheManager != null && result != null) {
                Cache cache = cacheManager.getCache("geocoding");
                if (cache != null) {
                    cache.put(address, result);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Cache write failed, result will not be cached: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            log.debug("Cache write failure details", e);
        }
    }

    private String normalize(String address) {
        if (address == null) {
            return "";
        }
        return address.toLowerCase(Locale.ROOT)
                .replace(',', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}