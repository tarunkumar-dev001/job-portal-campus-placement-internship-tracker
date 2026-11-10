package com.example.demo.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.PlacementOfficerProfile;

public interface PlacementOfficerProfileRepository extends JpaRepository<PlacementOfficerProfile, Long> {
    Optional<PlacementOfficerProfile> findByUserId(Long userId);
}
