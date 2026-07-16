package com.example.geocoder;

public record GeocodingResult(String address, double latitude, double longitude, String source) {
}