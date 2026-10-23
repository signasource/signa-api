package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationCourseRepository extends JpaRepository<OrganizationCourse, UUID> {

    @EntityGraph(attributePaths = {"course", "course.signLanguage"})
    List<OrganizationCourse> findByOrganizationIdOrderByContractedAtAsc(UUID organizationId);

    Optional<OrganizationCourse> findByOrganizationIdAndCourseId(
            UUID organizationId, UUID courseId);

    boolean existsByOrganizationIdAndCourseId(UUID organizationId, UUID courseId);
}
