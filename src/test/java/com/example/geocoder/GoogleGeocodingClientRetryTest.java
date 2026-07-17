package com.example.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Uses a real Spring context (rather than `new GoogleGeocodingClient(...)`) because the
// @Retry annotation is applied by an AOP proxy that Spring creates around the bean; a
// plain, manually-constructed instance would never go through that proxy.
@SpringBootTest
@Import({ TestcontainersConfiguration.class, GoogleGeocodingClientRetryTest.MockRestClientTestConfig.class })
class GoogleGeocodingClientRetryTest {

    @Autowired
    private GoogleGeocodingClient client;

    @Autowired
    private MockRestServiceServer server;

    @BeforeEach
    void resetServer() {
        server.reset();
    }

    @Test
    void retriesTransientFailuresAndSucceedsOnThirdAttempt() {
        server.expect(times(2), method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        String json = """
                {
                  "status": "OK",
                  "results": [
                    { "geometry": { "location": { "lat": 50.4501, "lng": 30.5234 } } }
                  ]
                }
                """;
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<Coordinates> result = client.geocode("Kyiv, Ukraine");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(50.4501);
        assertThat(result.get().longitude()).isEqualTo(30.5234);
        server.verify();
    }

    @Test
    void fallsBackToEmptyWhenAllRetriesExhausted() {
        server.expect(times(3), method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        Optional<Coordinates> result = client.geocode("Kyiv, Ukraine");

        assertThat(result).isEmpty();
        server.verify();
    }

    @TestConfiguration
    static class MockRestClientTestConfig {

        @Bean
        RestClient.Builder mockRestClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockRestServiceServer() {
            return MockRestServiceServer.bindTo(mockRestClientBuilder()).build();
        }

        @Bean
        @Primary
        RestClient mockedRestClient() {
            // force the binding above to happen before the builder is used
            mockRestServiceServer();
            return mockRestClientBuilder().build();
        }
    }
}
