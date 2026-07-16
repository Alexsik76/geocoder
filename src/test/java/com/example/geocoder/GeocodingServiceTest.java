package com.example.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private GeoLocationRepository repository;

    @Mock
    private GoogleGeocodingClient googleClient;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private GeocodingService service;



    @Test
    void returnsFromDatabaseWhenFound() {
        when(repository.findByAddress("Kyiv, Ukraine"))
                .thenReturn(Optional.of(new GeoLocation("Kyiv, Ukraine", 50.4501, 30.5234)));

        GeocodingResult result = service.geocode("Kyiv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void fetchesFromGoogleAndSavesWhenNotInDatabase() {
        when(repository.findByAddress("Lviv, Ukraine")).thenReturn(Optional.empty());
        when(googleClient.geocode("Lviv, Ukraine"))
                .thenReturn(Optional.of(new Coordinates(49.8397, 24.0297)));
        when(repository.save(any(GeoLocation.class)))
                .thenReturn(new GeoLocation("Lviv, Ukraine", 49.8397, 24.0297));

        GeocodingResult result = service.geocode("Lviv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("google");
        assertThat(result.longitude()).isEqualTo(24.0297);
        verify(repository).save(any(GeoLocation.class));
    }

    @Test
    void returnsNullWhenNotFoundAnywhere() {
        when(repository.findByAddress("Nowhere")).thenReturn(Optional.empty());
        when(googleClient.geocode("Nowhere")).thenReturn(Optional.empty());

        GeocodingResult result = service.geocode("Nowhere");

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void trimsWhitespaceBeforeLookup() {
        when(repository.findByAddress("Kyiv, Ukraine"))
                .thenReturn(Optional.of(new GeoLocation("Kyiv, Ukraine", 50.4501, 30.5234)));

        GeocodingResult result = service.geocode("  Kyiv, Ukraine  ");

        assertThat(result).isNotNull();
        verify(repository).findByAddress("Kyiv, Ukraine");
    }

    @Test
    void fallsBackToDatabaseWhenCacheIsUnavailable() {
        when(cacheManager.getCache("geocoding")).thenReturn(cache);
        when(cache.get("Kyiv, Ukraine"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        doThrow(new RedisConnectionFailureException("Unable to connect to Redis"))
                .when(cache).put(any(), any());
        when(repository.findByAddress("Kyiv, Ukraine"))
                .thenReturn(Optional.of(new GeoLocation("Kyiv, Ukraine", 50.4501, 30.5234)));

        GeocodingResult result = service.geocode("Kyiv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
    }
}