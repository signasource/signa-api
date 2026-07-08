package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.SignReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SignReportRepository extends JpaRepository<SignReport, UUID> {
    List<SignReport> findByStatus(ReportStatus status);

    List<SignReport> findByUserId(UUID userId);

    List<SignReport> findBySignId(UUID signId);
}
