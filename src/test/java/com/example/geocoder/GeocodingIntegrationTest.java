package com.example.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.parallel.ExecutionMode;




@SpringBootTest
@Execution(ExecutionMode.SAME_THREAD)
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
        cacheManager.getCache("geocoding").clear();
    }

     @Test
    void secondCallIsServedFromCacheAfterDatabaseLookup() {
        String address = ("db entry " + UUID.randomUUID()).toLowerCase();
        repository.save(new GeoLocation(address, 11.11, 22.22));

        GeocodingResult first = service.geocode(address);
        // remove the entry from the database: from now on it exists only in the cache
        repository.deleteAll();
        GeocodingResult second = service.geocode(address);

        assertThat(first).isNotNull();
        assertThat(first.source()).isEqualTo("database");
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
        GeocodingResult cachedResult = new GeocodingResult(address, 3.3, 3.3, "cache");
        Cache cache = cacheManager.getCache("geocoding");
        cache.put(address, cachedResult);
        awaitCacheWriteVisible(cache, address);

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("cache");
        assertThat(result.latitude()).isEqualTo(3.3);
        assertThat(result.longitude()).isEqualTo(3.3);
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
    private void awaitCacheWriteVisible(Cache cache, String key) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (cache.get(key) != null) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Cache write for key '" + key + "' did not become visible in time");
    }
}