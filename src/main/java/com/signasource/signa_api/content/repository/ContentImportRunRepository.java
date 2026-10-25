package com.signasource.signa_api.content.repository;

import com.signasource.signa_api.content.entity.ContentImportRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentImportRunRepository extends JpaRepository<ContentImportRun, UUID> {

    Page<ContentImportRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
