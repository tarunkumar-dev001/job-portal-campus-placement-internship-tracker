package com.example.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.entity.PlacementSelection;

public interface PlacementSelectionRepository extends JpaRepository<PlacementSelection, Long> {
    long countByDriveCollegeId(Long collegeId);
    long countByDriveId(Long driveId);

    @Query("select count(distinct selection.student.id) from PlacementSelection selection "
            + "where selection.drive.college.id = :collegeId and exists "
            + "(select profile.id from StudentProfile profile where profile.user.id = selection.student.id "
            + "and profile.college.id = :collegeId)")
    long countDistinctCollegeStudentsPlaced(@Param("collegeId") Long collegeId);

    @Query("select count(distinct selection.student.id) from PlacementSelection selection")
    long countDistinctStudentsPlacedPlatform();

    @Query("select profile.department, count(selection.id) from PlacementSelection selection, "
            + "StudentProfile profile where selection.drive.college.id = :collegeId "
            + "and profile.user.id = selection.student.id and profile.college.id = :collegeId "
            + "group by profile.department")
    List<Object[]> countSelectionsByDepartment(@Param("collegeId") Long collegeId);
}
