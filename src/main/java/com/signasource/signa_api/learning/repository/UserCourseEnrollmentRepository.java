package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCourseEnrollmentRepository extends JpaRepository<UserCourseEnrollment, UUID> {

    long countByUserIdAndStatus(UUID userId, EnrollmentStatus status);

    List<UserCourseEnrollment> findByUserId(UUID userId);

    Optional<UserCourseEnrollment> findByUserIdAndCourseVersionId(
            UUID userId, UUID courseVersionId);

    boolean existsByUserIdAndCourseVersionId(UUID userId, UUID courseVersionId);

    @EntityGraph(attributePaths = {"courseVersion", "courseVersion.course"})
    List<UserCourseEnrollment> findWithCourseByUserId(UUID userId);

    @EntityGraph(attributePaths = {"courseVersion", "courseVersion.course"})
    List<UserCourseEnrollment> findByUserIdAndCourseVersionCourseId(UUID userId, UUID courseId);

    @EntityGraph(attributePaths = {"courseVersion", "courseVersion.course"})
    List<UserCourseEnrollment> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    @EntityGraph(attributePaths = {"user", "courseVersion", "courseVersion.course"})
    List<UserCourseEnrollment> findByOrganizationIdAndCourseVersionCourseId(
            UUID organizationId, UUID courseId);

    long countByCourseVersionIdAndStatusNot(UUID courseVersionId, EnrollmentStatus status);

    @Query(
            "SELECT DISTINCT e.courseVersion.id FROM UserCourseEnrollment e "
                    + "WHERE e.organization.id = :organizationId "
                    + "AND e.courseVersion.course.id IN :courseIds "
                    + "AND e.status <> :excluded")
    List<UUID> findDistinctVersionIdsByOrganizationAndCourseIds(
            @Param("organizationId") UUID organizationId,
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("excluded") EnrollmentStatus excluded);
}
