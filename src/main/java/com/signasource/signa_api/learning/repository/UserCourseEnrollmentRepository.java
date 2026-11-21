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

    /** Enrollments those users got from the organization in those courses, course loaded. */
    @Query(
            "SELECT e FROM UserCourseEnrollment e "
                    + "JOIN FETCH e.courseVersion v JOIN FETCH v.course c "
                    + "WHERE e.organization.id = :organizationId "
                    + "AND e.user.id IN :userIds AND c.id IN :courseIds")
    List<UserCourseEnrollment> findOrganizationEnrollments(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds);

    /**
     * Every enrollment of those users in any version of those courses, version and course loaded.
     */
    @Query(
            "SELECT e FROM UserCourseEnrollment e "
                    + "JOIN FETCH e.courseVersion v JOIN FETCH v.course c "
                    + "WHERE e.user.id IN :userIds AND c.id IN :courseIds")
    List<UserCourseEnrollment> findWithCourseByUserIdInAndCourseIdIn(
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds);
}
