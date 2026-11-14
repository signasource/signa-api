package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCuit(String cuit);

    Optional<Organization> findByNameIgnoreCase(String name);

    List<Organization> findByStatus(OrganizationStatus status);

    Page<Organization> findByStatusIn(List<OrganizationStatus> statuses, Pageable pageable);

    long countByStatus(OrganizationStatus status);

    @Modifying
    @Query("delete from UserCourseEnrollment e where e.organization.id = :organizationId")
    void deleteEnrollmentsOf(@Param("organizationId") UUID organizationId);

    @Modifying
    @Query(
            "update LessonBlockAttempt a set a.organization = null "
                    + "where a.organization.id = :organizationId")
    void clearAttemptsOf(@Param("organizationId") UUID organizationId);

    /** Drops the organization from enrollments and attempts so its row can be deleted. */
    default void detachFromLearning(UUID organizationId) {
        deleteEnrollmentsOf(organizationId);
        clearAttemptsOf(organizationId);
    }
}
