package com.example.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GeocodingIntegrationTest {

    @Autowired
    private GeocodingService service;

    @Autowired
    private GeoLocationRepository repository;

    @MockitoBean
    private GoogleGeocodingClient googleClient;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache("geocoding");
        if (cache != null) {
            cache.clear();
            for (int attempt = 0; attempt < 50; attempt++) {
                if (cache.get("kyiv ukraine") == null) {
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    @Test
    void seedDataLookupIsConsistentForLegacyFormat() {
        GeocodingResult result = service.geocode("Kyiv, Ukraine");
        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
        assertThat(result.longitude()).isEqualTo(30.5234);
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void seedDataLookupIsConsistentForNormalizedFormat() {
        GeocodingResult result = service.geocode("kyiv ukraine");
        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
        assertThat(result.longitude()).isEqualTo(30.5234);
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void seedDataLookupIsConsistentForUppercaseFormat() {
        GeocodingResult result = service.geocode("KYIV,  UKRAINE");
        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
        assertThat(result.longitude()).isEqualTo(30.5234);
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void secondCallIsServedFromCacheAfterDatabaseLookup() {
        String address = ("db entry " + UUID.randomUUID()).toLowerCase();
        repository.save(new GeoLocation(address, 11.11, 22.22));

        GeocodingResult first = service.geocode(address);
        Cache cache = cacheManager.getCache("geocoding");
        awaitCacheValue(cache, address, v -> v instanceof Location);

        // remove the entry from the database: from now on it exists only in the cache
        repository.deleteAll();
        GeocodingResult second = service.geocode(address);

        assertThat(first).isNotNull();
        assertThat(first.source()).isEqualTo("database");
        Cache.ValueWrapper cachedValue = cache.get(address);
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue.get()).isInstanceOf(Location.class);
        assertThat(second).isNotNull();
        assertThat(second.source()).isEqualTo("cache");
        assertThat(second.latitude()).isEqualTo(first.latitude());
        assertThat(second.longitude()).isEqualTo(first.longitude());
        verify(googleClient, never()).geocode(address);
    }

    @Test
    void secondCallIsServedFromCacheAfterGoogleLookup() {
        String address = ("google entry " + UUID.randomUUID()).toLowerCase();
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(1.0, 2.0)));

        GeocodingResult first = service.geocode(address);
        Cache cache = cacheManager.getCache("geocoding");
        awaitCacheValue(cache, address, v -> v instanceof Location);

        // remove the entry from the database and "unteach" Google:
        // from now on the value exists only in the cache
        repository.deleteAll();
        when(googleClient.geocode(address)).thenReturn(Optional.empty());
        GeocodingResult second = service.geocode(address);

        assertThat(first).isNotNull();
        assertThat(first.source()).isEqualTo("google");
        assertThat(second).isNotNull();
        assertThat(second.source()).isEqualTo("cache");
        assertThat(second.latitude()).isEqualTo(first.latitude());
        assertThat(second.longitude()).isEqualTo(first.longitude());
    }

    @Test
    void unknownAddressGoesToGoogleAndIsPersisted() {
        String address = ("new place " + UUID.randomUUID()).toLowerCase();
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(46.4825, 30.7233)));

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("google");
        assertThat(repository.findByAddress(address)).isPresent();
    }

    @Test
    void addressUnknownEverywhereReturnsNullAndIsNotPersisted() {
        String address = ("nowhere " + UUID.randomUUID()).toLowerCase();
        when(googleClient.geocode(address)).thenReturn(Optional.empty());

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNull();
        assertThat(repository.findByAddress(address)).isEmpty();
    }

    @Test
    void cacheHasHighestPriorityOverDatabaseAndGoogle() {
        String address = ("priority test " + UUID.randomUUID()).toLowerCase();

        // Setup Google Client
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(1.1, 1.1)));

        // Setup Database
        repository.save(new GeoLocation(address, 2.2, 2.2));

        // Setup Cache
        Location cachedLocation = new Location(address, 3.3, 3.3);
        Cache cache = cacheManager.getCache("geocoding");
        cache.put(address, cachedLocation);
        awaitCacheValue(cache, address, v -> v instanceof Location);

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("cache");
        assertThat(result.latitude()).isEqualTo(3.3);
        assertThat(result.longitude()).isEqualTo(3.3);
    }

    @Test
    void legacyCacheEntryIsTreatedAsCacheMiss() {
        String address = ("legacy entry " + UUID.randomUUID()).toLowerCase();
        repository.save(new GeoLocation(address, 5.5, 6.6));

        // Put legacy GeocodingResult object into cache
        GeocodingResult legacyEntry = new GeocodingResult(address, 9.9, 9.9, "database");
        Cache cache = cacheManager.getCache("geocoding");
        cache.put(address, legacyEntry);
        awaitCacheValue(cache, address, v -> v instanceof GeocodingResult);

        // First call should treat legacy entry as cache miss and return from database
        GeocodingResult first = service.geocode(address);
        awaitCacheValue(cache, address, v -> v instanceof Location);

        assertThat(first).isNotNull();
        assertThat(first.source()).isEqualTo("database");
        assertThat(first.latitude()).isEqualTo(5.5);
        assertThat(first.longitude()).isEqualTo(6.6);

        // Second call should return from cache with source "cache" and Location object stored
        GeocodingResult second = service.geocode(address);

        assertThat(second).isNotNull();
        assertThat(second.source()).isEqualTo("cache");
        assertThat(second.latitude()).isEqualTo(5.5);
        assertThat(second.longitude()).isEqualTo(6.6);
    }

    // DefaultRedisCacheWriter.put() writes asynchronously (fire-and-forget) whenever
    // the RedisConnectionFactory also implements ReactiveRedisConnectionFactory, which
    // LettuceConnectionFactory does here since reactor-core is on the classpath - see
    // DefaultRedisCacheWriter's constructor (asynchronousWrites=true in that case) and
    // put(): `asyncCacheWriter.store(...).thenRun(...)` instead of a blocking execute().
    // So Cache.put() can return before the value is actually visible to Cache.get().
    // Raw RedisTemplate.opsForValue().set() is unaffected (always synchronous) and real
    // requests never hit this window, since the write and the read happen on separate
    // HTTP calls; only this test writes to the cache directly and reads it back with no
    // I/O in between, so it must wait the async write out before asserting.
    // See: https://github.com/spring-projects/spring-data-redis/issues/3348
    // ("RedisCacheWriter default changed from synchronous to asynchronous in 4.x
    // without clear migration notice")
    private void awaitCacheValue(Cache cache, String key, Predicate<Object> expected) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null && expected.test(wrapper.get())) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Cache value for key '" + key + "' did not satisfy condition in time");
    }
}