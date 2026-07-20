package ua.vn.lab.geocoder.controller;

import ua.vn.lab.geocoder.domain.GeocodingResult;
import ua.vn.lab.geocoder.repository.GeoLocationRepository;
import ua.vn.lab.geocoder.service.CacheInspector;
import ua.vn.lab.geocoder.service.GeocodingService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GeocoderWebController {

    private final GeocodingService service;
    private final GeoLocationRepository repository;
    private final CacheInspector cacheInspector;

    public GeocoderWebController(GeocodingService service,
            GeoLocationRepository repository,
            CacheInspector cacheInspector) {
        this.service = service;
        this.repository = repository;
        this.cacheInspector = cacheInspector;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/ui/geocode")
    public String getMapFragment(@RequestParam String address, Model model,
            HttpServletResponse response) {
        boolean blankInput = address.isBlank();
        GeocodingResult result = blankInput ? null : service.geocode(address);
        model.addAttribute("result", result);
        model.addAttribute("blankInput", blankInput);
        response.setHeader("HX-Trigger", "geocodeDone");
        return "fragments/map :: mapResult";
    }

    @GetMapping("/ui/tables")
    public String getTablesFragment(Model model) {
        model.addAttribute("dbEntries", repository.findTop20ByOrderByIdDesc());
        model.addAttribute("cacheEntries", cacheInspector.latestEntries());
        return "fragments/tables :: dataTables";
    }
}