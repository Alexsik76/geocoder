package ua.vn.lab.geocoder.domain;

public record GeocodingResult(String address, double latitude, double longitude, String source) {
}