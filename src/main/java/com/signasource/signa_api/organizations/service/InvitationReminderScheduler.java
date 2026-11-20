package com.signasource.signa_api.organizations.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationReminderScheduler {

    private final SeatInvitationService invitationService;

    @Scheduled(cron = "0 0 * * * *", zone = "America/Argentina/Buenos_Aires")
    public void sendDueReminders() {
        int sent = invitationService.sendDueAutoReminders(Instant.now());
        if (sent > 0) {
            log.info("Sent {} automatic invitation reminders", sent);
        }
    }
}
