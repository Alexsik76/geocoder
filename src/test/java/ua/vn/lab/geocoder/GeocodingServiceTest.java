package ua.vn.lab.geocoder;

import ua.vn.lab.geocoder.domain.Coordinates;
import ua.vn.lab.geocoder.domain.GeoLocation;
import ua.vn.lab.geocoder.domain.GeocodingResult;
import ua.vn.lab.geocoder.domain.Location;
import ua.vn.lab.geocoder.repository.GeoLocationRepository;
import ua.vn.lab.geocoder.service.GeocodingService;
import ua.vn.lab.geocoder.service.GoogleGeocodingClient;

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
    void returnsFromCacheWhenFound() {
        when(cacheManager.getCache("geocoding")).thenReturn(cache);
        org.springframework.cache.Cache.ValueWrapper wrapper = org.mockito.Mockito.mock(org.springframework.cache.Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(new Location("kyiv ukraine", 50.4501, 30.5234));
        when(cache.get("kyiv ukraine")).thenReturn(wrapper);

        GeocodingResult result = service.geocode("Kyiv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("cache");
        assertThat(result.address()).isEqualTo("kyiv ukraine");
        assertThat(result.latitude()).isEqualTo(50.4501);
        assertThat(result.longitude()).isEqualTo(30.5234);
        verify(repository, never()).findByAddress(any());
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void returnsFromDatabaseWhenFound() {
        when(repository.findByAddress("kyiv ukraine"))
                .thenReturn(Optional.of(new GeoLocation("kyiv ukraine", 50.4501, 30.5234)));

        GeocodingResult result = service.geocode("Kyiv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void fetchesFromGoogleAndSavesWhenNotInDatabase() {
        when(repository.findByAddress("lviv ukraine")).thenReturn(Optional.empty());
        when(googleClient.geocode("Lviv, Ukraine"))
                .thenReturn(Optional.of(new Coordinates(49.8397, 24.0297)));
        when(repository.save(any(GeoLocation.class)))
                .thenReturn(new GeoLocation("lviv ukraine", 49.8397, 24.0297));

        GeocodingResult result = service.geocode("Lviv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("google");
        assertThat(result.longitude()).isEqualTo(24.0297);
        verify(repository).save(any(GeoLocation.class));
    }

    @Test
    void returnsNullWhenNotFoundAnywhere() {
        when(repository.findByAddress("nowhere")).thenReturn(Optional.empty());
        when(googleClient.geocode("Nowhere")).thenReturn(Optional.empty());

        GeocodingResult result = service.geocode("Nowhere");

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void trimsWhitespaceBeforeLookup() {
        when(repository.findByAddress("kyiv ukraine"))
                .thenReturn(Optional.of(new GeoLocation("kyiv ukraine", 50.4501, 30.5234)));

        GeocodingResult result = service.geocode("  Kyiv, Ukraine  ");

        assertThat(result).isNotNull();
        verify(repository).findByAddress("kyiv ukraine");
    }

    @Test
    void fallsBackToDatabaseWhenCacheIsUnavailable() {
        when(cacheManager.getCache("geocoding")).thenReturn(cache);
        when(cache.get("kyiv ukraine"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        doThrow(new RedisConnectionFailureException("Unable to connect to Redis"))
                .when(cache).put(any(), any());
        when(repository.findByAddress("kyiv ukraine"))
                .thenReturn(Optional.of(new GeoLocation("kyiv ukraine", 50.4501, 30.5234)));

        GeocodingResult result = service.geocode("Kyiv, Ukraine");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("database");
        assertThat(result.latitude()).isEqualTo(50.4501);
    }

    @Test
    void normalizationResolvesDifferentSpellingsToSameDbQuery() {
        when(repository.findByAddress("kyiv ukraine"))
                .thenReturn(Optional.of(new GeoLocation("kyiv ukraine", 50.4501, 30.5234)));

        service.geocode("Kyiv, Ukraine");
        service.geocode("kyiv  ukraine");
        service.geocode("  KYIV, UKRAINE  ");

        verify(repository, org.mockito.Mockito.times(3)).findByAddress("kyiv ukraine");
    }

    @Test
    void googleGeocodeReceivesTrimmedOriginalInput() {
        when(repository.findByAddress("kyiv ukraine")).thenReturn(Optional.empty());
        when(googleClient.geocode("Kyiv, Ukraine"))
                .thenReturn(Optional.of(new Coordinates(50.4501, 30.5234)));
        when(repository.save(any(GeoLocation.class)))
                .thenReturn(new GeoLocation("kyiv ukraine", 50.4501, 30.5234));

        service.geocode("  Kyiv, Ukraine  ");

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(googleClient).geocode(captor.capture());
        assertThat(captor.getValue()).isEqualTo("Kyiv, Ukraine");
    }
}