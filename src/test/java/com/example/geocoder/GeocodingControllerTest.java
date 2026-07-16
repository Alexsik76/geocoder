package com.example.geocoder;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.cache.CacheManager;

@WebMvcTest(GeocodingController.class)
class GeocodingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeocodingService service;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void returnsOkWithResultWhenFound() throws Exception {
        when(service.geocode("Kyiv, Ukraine")).thenReturn(
                new GeocodingResult("Kyiv, Ukraine", 50.4501, 30.5234, "database"));

        mockMvc.perform(get("/api/geocode").param("address", "Kyiv, Ukraine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(50.4501))
                .andExpect(jsonPath("$.source").value("database"));
    }

    @Test
    void returnsNotFoundWhenAddressUnknown() throws Exception {
        when(service.geocode("Nowhere")).thenReturn(null);

        mockMvc.perform(get("/api/geocode").param("address", "Nowhere"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestWhenAddressMissing() throws Exception {
        mockMvc.perform(get("/api/geocode"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenAddressBlank() throws Exception {
        mockMvc.perform(get("/api/geocode").param("address", "   "))
                .andExpect(status().isBadRequest());
    }
}