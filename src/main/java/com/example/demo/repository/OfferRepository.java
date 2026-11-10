package com.example.demo.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.example.demo.entity.Offer;
public interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByApplicationId(Long applicationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") Long id);
    java.util.List<Offer> findByApplicationJobId(Long jobId);
    java.util.List<Offer> findByApplicationStudentId(Long studentId);
}
