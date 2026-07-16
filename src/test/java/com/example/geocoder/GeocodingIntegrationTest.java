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
        String address = "Db Entry " + UUID.randomUUID();
        repository.save(new GeoLocation(address, 11.11, 22.22));

        GeocodingResult first = service.geocode(address);
        // remove the entry from the database: from now on it exists only in the cache
        repository.deleteAll();
        GeocodingResult second = service.geocode(address);

        assertThat(first).isNotNull();
        assertThat(first.source()).isEqualTo("database");
        assertThat(second).isEqualTo(first);
        verify(googleClient, never()).geocode(address);
    }

    @Test
    void secondCallIsServedFromCacheAfterGoogleLookup() {
        String address = "Google Entry " + UUID.randomUUID();
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
        assertThat(second).isEqualTo(first);
    }

    @Test
    void unknownAddressGoesToGoogleAndIsPersisted() {
        String address = "New Place " + UUID.randomUUID();
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(46.4825, 30.7233)));

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("google");
        assertThat(repository.findByAddress(address)).isPresent();
    }

    @Test
    void addressUnknownEverywhereReturnsNullAndIsNotPersisted() {
        String address = "Nowhere " + UUID.randomUUID();
        when(googleClient.geocode(address)).thenReturn(Optional.empty());

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNull();
        assertThat(repository.findByAddress(address)).isEmpty();
    }

    @Test
    void cacheHasHighestPriorityOverDatabaseAndGoogle() {
        String address = "Priority Test " + UUID.randomUUID();

        // Setup Google Client
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(1.1, 1.1)));

        // Setup Database
        repository.save(new GeoLocation(address, 2.2, 2.2));

        // Setup Cache
        GeocodingResult cachedResult = new GeocodingResult(address, 3.3, 3.3, "cache");
        cacheManager.getCache("geocoding").put(address, cachedResult);

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("cache");
        assertThat(result.latitude()).isEqualTo(3.3);
        assertThat(result.longitude()).isEqualTo(3.3);
    }
}