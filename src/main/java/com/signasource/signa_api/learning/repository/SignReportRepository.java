package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.SignReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignReportRepository extends JpaRepository<SignReport, Long> {
    List<SignReport> findByStatus(ReportStatus status);
    List<SignReport> findByUserId(Long userId);
    List<SignReport> findBySignId(Long signId);
}
