package com.signasource.signa_api.waitlist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistRepository waitlistRepository;

    @InjectMocks private WaitlistService waitlistService;

    @Test
    void subscribe_savesNormalizedEmail() {
        when(waitlistRepository.existsByEmail("ana@mail.com")).thenReturn(false);

        waitlistService.subscribe("  Ana@Mail.com ");

        ArgumentCaptor<WaitlistEntry> saved = ArgumentCaptor.forClass(WaitlistEntry.class);
        verify(waitlistRepository).save(saved.capture());
        assertEquals("ana@mail.com", saved.getValue().getEmail());
        assertNotNull(saved.getValue().getCreatedAt());
    }

    @Test
    void subscribe_existingEmail_doesNothing() {
        when(waitlistRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        waitlistService.subscribe("ana@mail.com");

        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void subscribe_concurrentDuplicate_isIgnored() {
        when(waitlistRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(waitlistRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        waitlistService.subscribe("ana@mail.com");

        verify(waitlistRepository).save(any());
    }
}
