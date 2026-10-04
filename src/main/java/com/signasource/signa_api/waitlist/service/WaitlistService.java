package com.signasource.signa_api.waitlist.service;

import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository repository;

    @Transactional
    public void subscribe(String email) {
        if (!repository.existsByEmail(email)) {
            repository.save(WaitlistEntry.builder().email(email).build());
        }
    }
}
