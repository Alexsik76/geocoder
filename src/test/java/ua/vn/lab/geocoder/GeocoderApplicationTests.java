package ua.vn.lab.geocoder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GeocoderApplicationTests {

    @Test
    void contextLoads() {
    }
}