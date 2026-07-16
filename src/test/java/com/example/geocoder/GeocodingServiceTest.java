package com.example.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private GeoLocationRepository repository;

    @Mock
    private GoogleGeocodingClient googleClient;

    @InjectMocks
    private GeocodingService service;

    @Test
    void returnsFromDatabaseWhenFound() {
        when(repository.findByAddress("Kyiv, Ukraine"))
                .thenReturn(Optional.of(new GeoLocation("Kyiv, Ukraine", 50.4501, 30.5234)));

        Optional<GeocodingResult> result = service.geocode("Kyiv, Ukraine");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("database");
        assertThat(result.get().latitude()).isEqualTo(50.4501);
        verify(googleClient, never()).geocode(any());
    }

    @Test
    void fetchesFromGoogleAndSavesWhenNotInDatabase() {
        when(repository.findByAddress("Lviv, Ukraine")).thenReturn(Optional.empty());
        when(googleClient.geocode("Lviv, Ukraine"))
                .thenReturn(Optional.of(new Coordinates(49.8397, 24.0297)));
        when(repository.save(any(GeoLocation.class)))
                .thenReturn(new GeoLocation("Lviv, Ukraine", 49.8397, 24.0297));

        Optional<GeocodingResult> result = service.geocode("Lviv, Ukraine");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo("google");
        assertThat(result.get().longitude()).isEqualTo(24.0297);
        verify(repository).save(any(GeoLocation.class));
    }

    @Test
    void returnsEmptyWhenNotFoundAnywhere() {
        when(repository.findByAddress("Nowhere")).thenReturn(Optional.empty());
        when(googleClient.geocode("Nowhere")).thenReturn(Optional.empty());

        Optional<GeocodingResult> result = service.geocode("Nowhere");

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void trimsWhitespaceBeforeLookup() {
        when(repository.findByAddress("Kyiv, Ukraine"))
                .thenReturn(Optional.of(new GeoLocation("Kyiv, Ukraine", 50.4501, 30.5234)));

        Optional<GeocodingResult> result = service.geocode("  Kyiv, Ukraine  ");

        assertThat(result).isPresent();
        verify(repository).findByAddress("Kyiv, Ukraine");
    }
}