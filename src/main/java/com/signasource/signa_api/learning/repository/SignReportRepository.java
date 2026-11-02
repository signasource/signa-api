package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.SignReport;
import java.time.LocalDateTime;
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

    long countBySignIdAndStatusIn(UUID signId, List<ReportStatus> statuses);

    long countByStatus(ReportStatus status);

    @Query("SELECT DISTINCT r.sign.id FROM SignReport r WHERE r.status IN :statuses")
    List<UUID> findDistinctSignIdsByStatusIn(@Param("statuses") List<ReportStatus> statuses);

    /** Individual reports for the admin panel with optional filters, newest first. */
    @EntityGraph(attributePaths = {"sign", "sign.signLanguage", "user", "resolvedBy"})
    @Query(
            """
            SELECT r FROM SignReport r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:reason IS NULL OR r.reason = :reason)
              AND (:signQuery IS NULL OR LOWER(r.sign.meaning) LIKE LOWER(CONCAT('%', :signQuery, '%')))
              AND (:from IS NULL OR r.createdAt >= :from)
              AND (:to IS NULL OR r.createdAt <= :to)
            ORDER BY r.createdAt DESC
            """)
    List<SignReport> findForAdmin(
            @Param("status") ReportStatus status,
            @Param("reason") ReportReason reason,
            @Param("signQuery") String signQuery,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
