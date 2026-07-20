package ua.vn.lab.geocoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleGeocodingClientTest {

    private MockRestServiceServer server;
    private GoogleGeocodingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleGeocodingClient(builder.build(), "test-key");
    }

    @Test
    void returnsCoordinatesWhenStatusOk() {
        String json = """
                {
                  "status": "OK",
                  "results": [
                    { "geometry": { "location": { "lat": 50.4501, "lng": 30.5234 } } }
                  ]
                }
                """;
        server.expect(method(HttpMethod.GET))
                .andExpect(queryParam("address", "Kyiv,%20Ukraine"))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<Coordinates> result = client.geocode("Kyiv, Ukraine");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(50.4501);
        assertThat(result.get().longitude()).isEqualTo(30.5234);
        server.verify();
    }

    @Test
    void returnsEmptyWhenZeroResults() {
        String json = """
                { "status": "ZERO_RESULTS", "results": [] }
                """;
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<Coordinates> result = client.geocode("Nonexistent place");

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void returnsEmptyInsteadOfThrowingWhenGoogleRespondsWithError() {
        server.expect(method(HttpMethod.GET))
                .andRespond(withBadRequest());

        Optional<Coordinates> result = client.geocode("");

        assertThat(result).isEmpty();
        server.verify();
    }
}