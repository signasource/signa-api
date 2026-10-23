package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.SignReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SignReportRepository extends JpaRepository<SignReport, UUID> {
    List<SignReport> findByStatus(ReportStatus status);

    List<SignReport> findByUserId(UUID userId);

    List<SignReport> findBySignMeaning(String meaning);

    @EntityGraph(attributePaths = {"sign", "sign.signLanguage", "user"})
    List<SignReport> findByStatusIn(List<ReportStatus> statuses);

    @EntityGraph(attributePaths = {"sign", "sign.signLanguage", "user"})
    List<SignReport> findBySignId(UUID signId);

    long countBySignIdAndStatusIn(UUID signId, List<ReportStatus> statuses);

    long countByStatus(ReportStatus status);

    @Query("SELECT DISTINCT r.sign.id FROM SignReport r WHERE r.status IN :statuses")
    List<UUID> findDistinctSignIdsByStatusIn(@Param("statuses") List<ReportStatus> statuses);
}
