package com.example.geocoder;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoLocationRepository extends JpaRepository<GeoLocation, Long> {

    Optional<GeoLocation> findByAddress(String address);
}