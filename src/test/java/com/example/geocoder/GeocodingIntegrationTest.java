package com.example.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GeocodingIntegrationTest {

    @Autowired
    private GeocodingService service;

    @MockitoSpyBean
    private GeoLocationRepository repository;

    @MockitoBean
    private GoogleGeocodingClient googleClient;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @org.junit.jupiter.api.BeforeEach
    void clearCache() {
        cacheManager.getCache("geocoding").clear();
    }

    @Test
    void secondCallIsServedFromCacheWithoutTouchingDatabase() {
        String address = "Test City " + UUID.randomUUID();
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(1.0, 2.0)));

        GeocodingResult first = service.geocode(address);
        GeocodingResult second = service.geocode(address);

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
        verify(repository, times(1)).findByAddress(address);
        verify(googleClient, times(1)).geocode(address);
    }

    @Test
    void unknownAddressGoesToGoogleAndIsPersisted() {
        String address = "Odesa " + UUID.randomUUID();
        when(googleClient.geocode(address))
                .thenReturn(Optional.of(new Coordinates(46.4825, 30.7233)));

        GeocodingResult result = service.geocode(address);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("google");
        assertThat(repository.findByAddress(address)).isPresent();
    }

    @Test
    void returnsValueDirectlyFromCache() {
        String address = "Cache Only " + UUID.randomUUID();
        GeocodingResult planted = new GeocodingResult(address, 11.11, 22.22, "database");

        var cache = cacheManager.getCache("geocoding");
        cache.put(address, planted);

        GeocodingResult result = service.geocode(address);

        assertThat(result).isEqualTo(planted);
    }

}