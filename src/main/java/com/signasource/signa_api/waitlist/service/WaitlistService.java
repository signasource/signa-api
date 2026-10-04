package com.signasource.signa_api.waitlist.service;

import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    public void subscribe(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (waitlistRepository.existsByEmail(email)) {
            return;
        }
        try {
            waitlistRepository.save(
                    WaitlistEntry.builder().email(email).createdAt(Instant.now()).build());
        } catch (DataIntegrityViolationException alreadySubscribed) {
            return;
        }
    }
}
