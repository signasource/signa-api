package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCuit(String cuit);

    Optional<Organization> findByNameIgnoreCase(String name);

    List<Organization> findByStatus(OrganizationStatus status);

    Page<Organization> findByStatusIn(List<OrganizationStatus> statuses, Pageable pageable);

    long countByStatus(OrganizationStatus status);
}
