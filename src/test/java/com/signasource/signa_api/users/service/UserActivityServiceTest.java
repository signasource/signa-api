package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import com.signasource.signa_api.users.repository.UserDailyActivityRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    @Mock private UserDailyActivityRepository userDailyActivityRepository;

    @InjectMocks private UserActivityService userActivityService;

    private User user;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        today = LocalDate.now(ZoneOffset.UTC);
    }

    @Test
    void shouldCreateTodayRecord_WhenNoneExists() {
        when(userDailyActivityRepository.findByUserAndActivityDate(eq(user), eq(today)))
                .thenReturn(Optional.empty());

        userActivityService.recordActivity(user, 3);

        ArgumentCaptor<UserDailyActivity> captor = ArgumentCaptor.forClass(UserDailyActivity.class);
        verify(userDailyActivityRepository).save(captor.capture());
        assertEquals(today, captor.getValue().getActivityDate());
        assertEquals(3, captor.getValue().getMinutes());
    }

    @Test
    void shouldAccumulateMinutes_WhenTodayRecordExists() {
        UserDailyActivity existing =
                UserDailyActivity.builder().user(user).activityDate(today).minutes(5).build();
        when(userDailyActivityRepository.findByUserAndActivityDate(eq(user), eq(today)))
                .thenReturn(Optional.of(existing));

        userActivityService.recordActivity(user, 4);

        ArgumentCaptor<UserDailyActivity> captor = ArgumentCaptor.forClass(UserDailyActivity.class);
        verify(userDailyActivityRepository).save(captor.capture());
        assertEquals(9, captor.getValue().getMinutes());
    }

    @Test
    void shouldReturnZero_WhenNoRecordForToday() {
        when(userDailyActivityRepository.findByUserAndActivityDate(eq(user), any()))
                .thenReturn(Optional.empty());

        assertEquals(0, userActivityService.getMinutesToday(user));
    }

    @Test
    void shouldReturnStoredMinutes_WhenRecordExistsForToday() {
        UserDailyActivity existing =
                UserDailyActivity.builder().user(user).activityDate(today).minutes(12).build();
        when(userDailyActivityRepository.findByUserAndActivityDate(eq(user), any()))
                .thenReturn(Optional.of(existing));

        assertEquals(12, userActivityService.getMinutesToday(user));
    }
}
