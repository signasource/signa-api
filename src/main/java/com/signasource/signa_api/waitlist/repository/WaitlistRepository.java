package com.signasource.signa_api.waitlist.repository;

import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    boolean existsByEmail(String email);

    Page<WaitlistEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
