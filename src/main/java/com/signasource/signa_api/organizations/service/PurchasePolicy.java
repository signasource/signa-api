package com.signasource.signa_api.organizations.service;

import java.time.Duration;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class PurchasePolicy {

    private final int minDays;
    private final int maxDays;
    private final int invitationDays;
    private final int autoReminderAfterDays;
    private final int manualReminderCooldownHours;
    private final int maxSeats;
    private final int withdrawalDays;

    public PurchasePolicy(
            @Value("${organizations.purchases.min-days:15}") int minDays,
            @Value("${organizations.purchases.max-days:60}") int maxDays,
            @Value("${organizations.purchases.invitation-days:10}") int invitationDays,
            @Value("${organizations.purchases.auto-reminder-after-days:5}")
                    int autoReminderAfterDays,
            @Value("${organizations.purchases.manual-reminder-cooldown-hours:24}")
                    int manualReminderCooldownHours,
            @Value("${organizations.purchases.max-seats:500}") int maxSeats,
            @Value("${organizations.purchases.withdrawal-days:10}") int withdrawalDays) {
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.invitationDays = invitationDays;
        this.autoReminderAfterDays = autoReminderAfterDays;
        this.manualReminderCooldownHours = manualReminderCooldownHours;
        this.maxSeats = maxSeats;
        this.withdrawalDays = withdrawalDays;
    }

    public boolean isValidDuration(int durationDays) {
        return durationDays >= minDays && durationDays <= maxDays;
    }

    /** How long after paying an organization can cancel a purchase for a full refund. */
    public Duration withdrawalWindow() {
        return Duration.ofDays(withdrawalDays);
    }

    public Duration invitationWindow() {
        return Duration.ofDays(invitationDays);
    }

    public Duration autoReminderDelay() {
        return Duration.ofDays(autoReminderAfterDays);
    }

    public Duration manualReminderCooldown() {
        return Duration.ofHours(manualReminderCooldownHours);
    }
}
