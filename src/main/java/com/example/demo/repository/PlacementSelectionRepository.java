package com.example.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.PlacementSelection;

public interface PlacementSelectionRepository extends JpaRepository<PlacementSelection, Long> {
    List<PlacementSelection> findByDriveCollegeId(Long collegeId);
    long countByDriveId(Long driveId);
}
