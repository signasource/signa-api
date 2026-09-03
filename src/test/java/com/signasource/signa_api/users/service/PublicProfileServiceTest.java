package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.gamification.service.AchievementService;
import com.signasource.signa_api.gamification.service.UserStatsService;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.RelationStatus;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private UserStatsService userStatsService;
    @Mock private AchievementService achievementService;
    @Mock private CourseTrackingService courseTrackingService;
    @Mock private FriendshipRepository friendshipRepository;

    @InjectMocks private PublicProfileService publicProfileService;

    private User target;
    private User viewer;

    @BeforeEach
    void setUp() {
        target = new User();
        target.setId(UUID.randomUUID());
        target.setUsername("target");
        target.setName("Target User");
        target.setEnabled(true);
        target.setAccountVisibility(AccountVisibility.PUBLIC);

        viewer = new User();
        viewer.setId(UUID.randomUUID());
        viewer.setUsername("viewer");
        viewer.setName("Viewer User");
        viewer.setEnabled(true);
    }

    private void stubProgress() {
        when(userStatsRepository.findByUserId(target.getId()))
                .thenReturn(
                        Optional.of(
                                UserStats.builder()
                                        .currentStreak(6)
                                        .longestStreak(11)
                                        .totalXp(3400L)
                                        .weeklyXp(220)
                                        .learnedSignsCount(48)
                                        .build()));
        when(userStatsService.getWeeklyXpBreakdown(target))
                .thenReturn(List.of(new DailyXpResponse(LocalDate.now(), 40)));
        when(achievementService.getAchievements(target, null, true)).thenReturn(List.of());
        when(courseTrackingService.getUserCourseProgress(target)).thenReturn(List.of());
    }

    @Test
    void getByUsername_ThrowsWhenTheUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> publicProfileService.getByUsername("ghost", viewer));
    }

    /** A deleted account is disabled, not removed; it must read as missing. */
    @Test
    void getByUsername_ThrowsWhenTheAccountIsDisabled() {
        target.setEnabled(false);
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));

        assertThrows(
                NotFoundException.class,
                () -> publicProfileService.getByUsername("target", viewer));
    }

    @Test
    void getByUsername_ReturnsTheFullProfileForAPublicAccount() {
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findFriendshipBetween(viewer, target))
                .thenReturn(Optional.empty());
        when(userSettingsRepository.findByUserId(target.getId()))
                .thenReturn(Optional.of(settingsWithColor("#7857FF")));
        stubProgress();

        PublicUserProfileResponse profile = publicProfileService.getByUsername("target", viewer);

        assertTrue(profile.visible());
        assertEquals(RelationStatus.NONE, profile.relation());
        assertEquals("#7857FF", profile.profileHeaderColor());
        assertEquals(6, profile.stats().currentStreak());
        assertEquals(3400L, profile.stats().totalXp());
        assertEquals(1, profile.weeklyXp().size());
    }

    @Test
    void getByUsername_FallsBackToWhiteWhenTheUserHasNoSettings() {
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findFriendshipBetween(viewer, target))
                .thenReturn(Optional.empty());
        when(userSettingsRepository.findByUserId(target.getId())).thenReturn(Optional.empty());
        stubProgress();

        assertEquals(
                "#FFFFFF",
                publicProfileService.getByUsername("target", viewer).profileHeaderColor());
    }

    @Test
    void getByUsername_ResolvesTheRelationFromTheViewerPointOfView() {
        Friendship pending = new Friendship();
        pending.setRequester(viewer);
        pending.setAddressee(target);
        pending.setStatus(FriendshipStatus.PENDING);

        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findFriendshipBetween(viewer, target))
                .thenReturn(Optional.of(pending));
        when(userSettingsRepository.findByUserId(target.getId())).thenReturn(Optional.empty());
        stubProgress();

        assertEquals(
                RelationStatus.OUTGOING,
                publicProfileService.getByUsername("target", viewer).relation());
    }

    /**
     * A private account still resolves — otherwise a stranger who found it through search could not
     * send a request — but carries no progress at all.
     */
    @Test
    void getByUsername_HidesTheProgressOfAPrivateAccountFromAStranger() {
        target.setAccountVisibility(AccountVisibility.PRIVATE);

        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findFriendshipBetween(viewer, target))
                .thenReturn(Optional.empty());

        PublicUserProfileResponse profile = publicProfileService.getByUsername("target", viewer);

        assertFalse(profile.visible());
        assertEquals(target.getId(), profile.id());
        assertEquals("Target User", profile.name());
        assertEquals(RelationStatus.NONE, profile.relation());
        assertEquals(0, profile.stats().currentStreak());
        assertTrue(profile.achievements().isEmpty());
        assertTrue(profile.courses().isEmpty());
        verifyNoInteractions(userStatsService, achievementService, courseTrackingService);
    }

    @Test
    void getByUsername_HidesAPrivateAccountFromAnAnonymousViewer() {
        target.setAccountVisibility(AccountVisibility.PRIVATE);
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));

        assertFalse(publicProfileService.getByUsername("target", null).visible());
    }

    @Test
    void getByUsername_OpensAPrivateAccountToAnAcceptedFriend() {
        target.setAccountVisibility(AccountVisibility.PRIVATE);

        Friendship accepted = new Friendship();
        accepted.setRequester(viewer);
        accepted.setAddressee(target);
        accepted.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findFriendshipBetween(viewer, target))
                .thenReturn(Optional.of(accepted));
        when(userSettingsRepository.findByUserId(target.getId())).thenReturn(Optional.empty());
        stubProgress();

        PublicUserProfileResponse profile = publicProfileService.getByUsername("target", viewer);

        assertTrue(profile.visible());
        assertEquals(RelationStatus.FRIEND, profile.relation());
    }

    @Test
    void getByUsername_OpensAPrivateAccountToItsOwner() {
        target.setAccountVisibility(AccountVisibility.PRIVATE);
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(userSettingsRepository.findByUserId(target.getId())).thenReturn(Optional.empty());
        stubProgress();

        assertTrue(publicProfileService.getByUsername("target", target).visible());
    }

    @Test
    void getByUsername_ReturnsZeroedStatsWhenTheUserHasNoStatsRow() {
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(friendshipRepository.findFriendshipBetween(viewer, target))
                .thenReturn(Optional.empty());
        when(userSettingsRepository.findByUserId(target.getId())).thenReturn(Optional.empty());
        when(userStatsRepository.findByUserId(target.getId())).thenReturn(Optional.empty());
        when(userStatsService.getWeeklyXpBreakdown(target)).thenReturn(List.of());
        when(achievementService.getAchievements(any(), any(), any())).thenReturn(List.of());
        when(courseTrackingService.getUserCourseProgress(target)).thenReturn(List.of());

        PublicUserProfileResponse profile = publicProfileService.getByUsername("target", viewer);

        assertTrue(profile.visible());
        assertEquals(0, profile.stats().currentStreak());
        assertEquals(0L, profile.stats().totalXp());
    }

    private UserSettings settingsWithColor(String color) {
        UserSettings settings = new UserSettings();
        settings.setProfileHeaderColor(color);
        return settings;
    }
}
