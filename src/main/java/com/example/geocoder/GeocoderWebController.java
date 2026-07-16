package com.example.geocoder;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GeocoderWebController {

    private final GeocodingService service;
    private final CacheManager cacheManager;

    public GeocoderWebController(GeocodingService service, CacheManager cacheManager) {
        this.service = service;
        this.cacheManager = cacheManager;
    }

    // Отдает главную страницу
    @GetMapping("/")
    public String index() {
        return "index";
    }

    // Возвращает только HTML-фрагмент с картой и координатами для HTMX
    @GetMapping("/ui/geocode")
    public String getMapFragment(@RequestParam String address, Model model) {
        // Determine if the value is already cached before calling the service
        boolean cacheHit = false;
        if (cacheManager != null) {
            var cache = cacheManager.getCache("geocoding");
            if (cache != null && cache.get(address) != null) {
                cacheHit = true;
            }
        }
        GeocodingResult result = service.geocode(address);
        model.addAttribute("result", result);
        model.addAttribute("cacheHit", cacheHit);
        return "fragments/map :: mapResult";
    }
}