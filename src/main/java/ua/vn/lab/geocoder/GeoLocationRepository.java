package ua.vn.lab.geocoder;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoLocationRepository extends JpaRepository<GeoLocation, Long> {

    Optional<GeoLocation> findByAddress(String address);
    List<GeoLocation> findTop20ByOrderByIdDesc();
}