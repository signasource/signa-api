package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.Sign;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SignRepository extends JpaRepository<Sign, UUID> {
    Page<Sign> findBySignLanguageId(UUID signLanguageId, Pageable pageable);

    Page<Sign> findBySignLanguageIdAndMeaningContainingIgnoreCase(
            UUID signLanguageId, String meaning, Pageable pageable);

    Optional<Sign> findByMeaningIgnoreCase(String meaning);

    List<Sign> findByMeaningIn(Collection<String> meanings);

    boolean existsByMeaningIgnoreCase(String meaning);

    Page<Sign> findByMeaningContainingIgnoreCase(String meaning, Pageable pageable);

    /** Case-insensitive {@code IN}: callers pass the meanings already lower-cased. */
    @Query("SELECT s FROM Sign s WHERE lower(s.meaning) IN :lowered")
    List<Sign> findByLowerMeaningIn(@Param("lowered") Collection<String> lowered);
}
