package com.example.geocoder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GeocoderWebController {

    private final GeocodingService service;

    public GeocoderWebController(GeocodingService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/ui/geocode")
    public String getMapFragment(@RequestParam String address, Model model) {
        GeocodingResult result = service.geocode(address);
        model.addAttribute("result", result);
        return "fragments/map :: mapResult";
    }
}