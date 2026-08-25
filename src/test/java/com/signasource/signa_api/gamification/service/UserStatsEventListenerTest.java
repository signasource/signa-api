package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.event.SignsLearnedEvent;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
class UserStatsEventListenerTest {

    @Mock private UserStatsRepository userStatsRepository;
    @Mock private UserDailyXpRepository userDailyXpRepository;
    @Mock private UserLearnedSignRepository userLearnedSignRepository;

    @InjectMocks private UserStatsEventListener userStatsEventListener;

    private UUID userId;
    private UUID courseVersionId;
    private User mockUser;
    private CourseVersion mockCourseVersion;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseVersionId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
        mockCourseVersion = new CourseVersion();
        mockCourseVersion.setId(courseVersionId);
    }

    private SignsLearnedEvent signsEvent(List<String> signs) {
        return new SignsLearnedEvent(this, mockUser, signs, mockCourseVersion);
    }

    private static Instant startOfCurrentWeek() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    @Test
    void handleXpEarnedEvent_CreatesStats_WhenUserHasNone() {
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userDailyXpRepository.findByUserAndXpDate(eq(mockUser), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(userDailyXpRepository.save(any(UserDailyXp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 50));

        ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
        verify(userStatsRepository).save(captor.capture());
        UserStats saved = captor.getValue();
        assertEquals(50L, saved.getTotalXp());
        assertEquals(50, saved.getWeeklyXp());
        assertEquals(mockUser, saved.getUser());
    }

    @Test
    void handleXpEarnedEvent_AccumulatesXp_WithinSameWeek() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .totalXp(100)
                        .weeklyXp(30)
                        .updatedAt(startOfCurrentWeek())
                        .build();
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userDailyXpRepository.findByUserAndXpDate(eq(mockUser), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(userDailyXpRepository.save(any(UserDailyXp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 20));

        assertEquals(120L, stats.getTotalXp());
        assertEquals(50, stats.getWeeklyXp());
    }

    @Test
    void shouldSaveRowsAndIncrementCount_WhenSignsAreNewToCourseAndGlobally() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .learnedSignsCount(2)
                        .updatedAt(Instant.now())
                        .build();
        when(userLearnedSignRepository.existsByUserIdAndSignAndCourseVersionId(
                        eq(userId), anyString(), eq(courseVersionId)))
                .thenReturn(false);
        when(userLearnedSignRepository.existsByUserIdAndSign(eq(userId), anyString()))
                .thenReturn(false);
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleSignsLearnedEvent(signsEvent(List.of("hola", "gracias")));

        verify(userLearnedSignRepository, times(2)).save(any(UserLearnedSign.class));
        assertEquals(4, stats.getLearnedSignsCount());
    }

    @Test
    void shouldSaveRowButNotIncrementCount_WhenSignIsNewToCourseButAlreadyGlobal() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .learnedSignsCount(1)
                        .updatedAt(Instant.now())
                        .build();
        when(userLearnedSignRepository.existsByUserIdAndSignAndCourseVersionId(
                        userId, "hola", courseVersionId))
                .thenReturn(false);
        when(userLearnedSignRepository.existsByUserIdAndSign(userId, "hola")).thenReturn(true);

        userStatsEventListener.handleSignsLearnedEvent(signsEvent(List.of("hola")));

        verify(userLearnedSignRepository, times(1)).save(any(UserLearnedSign.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
        assertEquals(1, stats.getLearnedSignsCount());
    }

    @Test
    void shouldDoNothingWhenAllSignsAlreadyInThisCourse() {
        when(userLearnedSignRepository.existsByUserIdAndSignAndCourseVersionId(
                        eq(userId), anyString(), eq(courseVersionId)))
                .thenReturn(true);

        userStatsEventListener.handleSignsLearnedEvent(signsEvent(List.of("hola")));

        verify(userLearnedSignRepository, never()).save(any(UserLearnedSign.class));
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void shouldHandleMixedSigns_SomeNewToCourseOthersNot() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .learnedSignsCount(0)
                        .updatedAt(Instant.now())
                        .build();
        when(userLearnedSignRepository.existsByUserIdAndSignAndCourseVersionId(
                        userId, "hola", courseVersionId))
                .thenReturn(true);
        when(userLearnedSignRepository.existsByUserIdAndSignAndCourseVersionId(
                        userId, "chau", courseVersionId))
                .thenReturn(false);
        when(userLearnedSignRepository.existsByUserIdAndSign(userId, "chau")).thenReturn(false);
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleSignsLearnedEvent(signsEvent(List.of("hola", "chau")));

        verify(userLearnedSignRepository, times(1)).save(any(UserLearnedSign.class));
        assertEquals(1, stats.getLearnedSignsCount());
    }

    @Test
    void handleXpEarnedEvent_ResetsWeeklyXp_WhenLastUpdateWasBeforeCurrentWeek() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .totalXp(200)
                        .weeklyXp(80)
                        .updatedAt(startOfCurrentWeek().minusSeconds(1))
                        .build();
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userDailyXpRepository.findByUserAndXpDate(eq(mockUser), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(userDailyXpRepository.save(any(UserDailyXp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 10));

        assertEquals(210L, stats.getTotalXp());
        assertEquals(10, stats.getWeeklyXp());
    }

    @Test
    void handleXpEarnedEvent_CreatesDailyXp_WhenNoRecordExistsForToday() {
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userDailyXpRepository.findByUserAndXpDate(eq(mockUser), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(userDailyXpRepository.save(any(UserDailyXp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 75));

        ArgumentCaptor<UserDailyXp> captor = ArgumentCaptor.forClass(UserDailyXp.class);
        verify(userDailyXpRepository).save(captor.capture());
        UserDailyXp saved = captor.getValue();
        assertEquals(75, saved.getXpEarned());
        assertEquals(LocalDate.now(ZoneOffset.UTC), saved.getXpDate());
        assertEquals(mockUser, saved.getUser());
    }

    @Test
    void handleXpEarnedEvent_AccumulatesDailyXp_WhenRecordAlreadyExistsForToday() {
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserDailyXp existing =
                UserDailyXp.builder()
                        .user(mockUser)
                        .xpDate(LocalDate.now(ZoneOffset.UTC))
                        .xpEarned(40)
                        .build();
        when(userDailyXpRepository.findByUserAndXpDate(eq(mockUser), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(userDailyXpRepository.save(any(UserDailyXp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 30));

        assertEquals(70, existing.getXpEarned());
    }
}
