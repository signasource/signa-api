package com.signasource.signa_api.organizations.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationReminderSchedulerTest {

    @Mock private SeatInvitationService invitationService;

    @InjectMocks private InvitationReminderScheduler scheduler;

    @Test
    void shouldSendTheDueReminders() {
        when(invitationService.sendDueAutoReminders(any())).thenReturn(2, 0);

        scheduler.sendDueReminders();
        scheduler.sendDueReminders();

        verify(invitationService, times(2)).sendDueAutoReminders(any());
    }
}
