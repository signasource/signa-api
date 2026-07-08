package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.Sign;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SignRepository extends JpaRepository<Sign, UUID> {
    Page<Sign> findBySignLanguageId(UUID signLanguageId, Pageable pageable);

    Page<Sign> findBySignLanguageIdAndMeaningContainingIgnoreCase(
            UUID signLanguageId, String meaning, Pageable pageable);
}
