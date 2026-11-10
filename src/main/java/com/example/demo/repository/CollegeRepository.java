package com.example.demo.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.College;

public interface CollegeRepository extends JpaRepository<College, Long> {
    Optional<College> findByCodeIgnoreCase(String code);
}
