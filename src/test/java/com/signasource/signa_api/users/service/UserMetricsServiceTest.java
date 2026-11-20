package com.signasource.signa_api.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.gamification.dto.ProfileMetricsResponse;
import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserDailyActivityRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserMetricsServiceTest {

    @Mock UserDailyXpRepository xpRepo;
    @Mock UserDailyActivityRepository activityRepo;
    @Mock UserLearnedSignRepository learnedRepo;
    @Mock UserSettingsRepository settingsRepo;
    @InjectMocks UserMetricsService service;

    @Test
    void sumsXpSignsAndCountsDaysGoalReached() {
        User user = User.builder().id(UUID.randomUUID()).build();
        LocalDate to = ArgentinaTime.today();
        LocalDate from = to.minusDays(2);
        UserSettings settings = new UserSettings();
        settings.setDailyGoalMinutes(10);
        when(settingsRepo.findByUserId(user.getId())).thenReturn(Optional.of(settings));
        when(xpRepo.findByUserAndXpDateBetween(user, from, to))
                .thenReturn(
                        List.of(
                                UserDailyXp.builder().xpDate(from).xpEarned(30).build(),
                                UserDailyXp.builder().xpDate(to).xpEarned(20).build()));
        when(activityRepo.findByUserAndActivityDateBetween(user, from, to))
                .thenReturn(
                        List.of(
                                UserDailyActivity.builder().activityDate(from).minutes(10).build(),
                                UserDailyActivity.builder()
                                        .activityDate(to.minusDays(1))
                                        .minutes(9)
                                        .build()));
        when(learnedRepo.countDistinctLearnedBetween(
                        eq(user.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(4L);

        ProfileMetricsResponse r = service.getMetrics(user, from, to);

        assertThat(r.totalDays()).isEqualTo(3);
        assertThat(r.xpEarned()).isEqualTo(50);
        assertThat(r.signsLearned()).isEqualTo(4);
        assertThat(r.daysGoalReached()).isEqualTo(1);
        assertThat(r.days()).hasSize(3);
    }

    @Test
    void rejectsInvertedRange() {
        User user = User.builder().id(UUID.randomUUID()).build();
        LocalDate d = ArgentinaTime.today();
        assertThatThrownBy(() -> service.getMetrics(user, d, d.minusDays(1)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void rejectsRangeOverOneYear() {
        User user = User.builder().id(UUID.randomUUID()).build();
        LocalDate to = ArgentinaTime.today();
        assertThatThrownBy(() -> service.getMetrics(user, to.minusDays(400), to))
                .isInstanceOf(InvalidInputException.class);
    }
}
