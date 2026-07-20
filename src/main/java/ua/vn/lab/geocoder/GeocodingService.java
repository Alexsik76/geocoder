package ua.vn.lab.geocoder;

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

    public GeocodingService(CacheManager cacheManager, GeoLocationRepository repository,
            GoogleGeocodingClient googleClient) {
        this.repository = repository;
        this.googleClient = googleClient;
        this.cacheManager = cacheManager;
    }

    public GeocodingResult geocode(String rawAddress) {
        String address = normalize(rawAddress);

        Location cached = getFromCache(address);
        if (cached != null)
            return toResult(cached, "cache");

        Location fromDb = getFromDatabase(address);
        if (fromDb != null)
            return toResult(fromDb, "database");

        Location fromGoogle = getFromGoogle(address, rawAddress);
        if (fromGoogle != null)
            return toResult(fromGoogle, "google");

        return null;
    }

    private GeocodingResult toResult(Location location, String source) {
        return new GeocodingResult(location.address(), location.latitude(), location.longitude(), source);
    }

    private Location getFromCache(String address) {
        try {
            Cache cache = cacheManager.getCache("geocoding");
            Cache.ValueWrapper cached = cache.get(address);
            if (cached != null && cached.get() instanceof Location location) {
                return location;
            }
        } catch (RuntimeException e) {
            log.warn("Cache read failed, falling back to database/Google: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            log.debug("Cache read failure details", e);
        }
        return null;
    }

    private Location getFromDatabase(String address) {
        Optional<GeoLocation> fromDb = repository.findByAddress(address);
        if (fromDb.isEmpty()) {
            return null;
        }
        Location location = fromDb.get().toLocation();
        putToCache(address, location);
        return location;
    }

    private Location getFromGoogle(String address, String rawAddress) {
        String originalTrimmed = rawAddress == null ? "" : rawAddress.trim();
        Optional<Coordinates> fromGoogle = googleClient.geocode(originalTrimmed);
        if (fromGoogle.isEmpty()) {
            return null;
        }

        Coordinates coordinates = fromGoogle.get();
        GeoLocation saved = repository.save(
                new GeoLocation(address, coordinates.latitude(), coordinates.longitude()));
        Location location = saved.toLocation();
        putToCache(address, location);
        return location;
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