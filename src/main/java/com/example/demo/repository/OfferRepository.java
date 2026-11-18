package com.example.demo.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.example.demo.entity.Offer;
import java.util.List;
public interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByApplicationId(Long applicationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") Long id);
    java.util.List<Offer> findByApplicationJobId(Long jobId);
    java.util.List<Offer> findByApplicationStudentId(Long studentId);

    @Query("select offer.status, count(offer.id) from Offer offer "
            + "where offer.application.job.recruiterId = :recruiterId group by offer.status")
    List<Object[]> countByStatusForRecruiter(@Param("recruiterId") Long recruiterId);

    @Query("select offer.status, count(offer.id) from Offer offer "
            + "where offer.application.student.id = :studentId group by offer.status")
    List<Object[]> countByStatusForStudent(@Param("studentId") Long studentId);

    long countByApplicationStudentId(Long studentId);
}
