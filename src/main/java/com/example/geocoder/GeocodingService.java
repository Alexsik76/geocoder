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

    private final GeoLocationRepository repository;
    private final GoogleGeocodingClient googleClient;




    public GeocodingService(CacheManager cacheManager, GeoLocationRepository repository, GoogleGeocodingClient googleClient) {
        this.repository = repository;
        this.googleClient = googleClient;
        this.cacheManager = cacheManager;
    }

    public GeocodingResult geocode(String rawAddress) {
        String normalizedAddress = normalize(rawAddress);

        Location cachedLocation = getFromCache(normalizedAddress);
        if (cachedLocation != null) {
            return new GeocodingResult(cachedLocation.address(), cachedLocation.latitude(), cachedLocation.longitude(), "cache");
        }

        Optional<GeoLocation> fromDb = repository.findByAddress(normalizedAddress);
        if (fromDb.isPresent()) {
            GeoLocation entity = fromDb.get();
            Location location = new Location(entity.getAddress(), entity.getLatitude(), entity.getLongitude());
            putToCache(normalizedAddress, location);
            return new GeocodingResult(location.address(), location.latitude(), location.longitude(), "database");
        }

        String originalTrimmed = rawAddress == null ? "" : rawAddress.trim();
        Optional<Coordinates> fromGoogle = googleClient.geocode(originalTrimmed);
        if (fromGoogle.isEmpty()) {
            return null;
        }

        Coordinates coordinates = fromGoogle.get();
        GeoLocation saved = repository.save(
                new GeoLocation(normalizedAddress, coordinates.latitude(), coordinates.longitude()));
        Location location = new Location(saved.getAddress(), saved.getLatitude(), saved.getLongitude());
        putToCache(normalizedAddress, location);
        return new GeocodingResult(location.address(), location.latitude(), location.longitude(), "google");
    }

    private Location getFromCache(String address) {
        try {
            if (cacheManager != null) {
                Cache cache = cacheManager.getCache("geocoding");
                if (cache != null) {
                    Cache.ValueWrapper cached = cache.get(address);
                    if (cached != null) {
                        Object value = cached.get();
                        if (value instanceof Location location) {
                            return location;
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

    private void putToCache(String address, Location location) {
        try {
            if (cacheManager != null && location != null) {
                Cache cache = cacheManager.getCache("geocoding");
                if (cache != null) {
                    cache.put(address, location);
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