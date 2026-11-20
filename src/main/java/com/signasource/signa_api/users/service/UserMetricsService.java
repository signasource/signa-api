package com.signasource.signa_api.users.service;

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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserMetricsService {

    static final int MAX_RANGE_DAYS = 366;

    private final UserDailyXpRepository userDailyXpRepository;
    private final UserDailyActivityRepository userDailyActivityRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional(readOnly = true)
    public ProfileMetricsResponse getMetrics(User user, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidInputException("'from' must not be after 'to'");
        }
        if (to.isAfter(ArgentinaTime.today())) {
            to = ArgentinaTime.today();
            if (from.isAfter(to)) {
                from = to;
            }
        }
        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (totalDays > MAX_RANGE_DAYS) {
            throw new InvalidInputException("The range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        int goal =
                userSettingsRepository
                        .findByUserId(user.getId())
                        .map(UserSettings::getDailyGoalMinutes)
                        .orElse(15);

        Map<LocalDate, Integer> xpByDate =
                userDailyXpRepository.findByUserAndXpDateBetween(user, from, to).stream()
                        .collect(
                                Collectors.toMap(
                                        UserDailyXp::getXpDate,
                                        UserDailyXp::getXpEarned,
                                        Integer::sum));
        Map<LocalDate, Integer> minutesByDate =
                userDailyActivityRepository
                        .findByUserAndActivityDateBetween(user, from, to)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        UserDailyActivity::getActivityDate,
                                        UserDailyActivity::getMinutes,
                                        Integer::sum));

        List<ProfileMetricsResponse.Day> days = new ArrayList<>();
        int xp = 0;
        int reached = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int dayXp = xpByDate.getOrDefault(d, 0);
            int minutes = minutesByDate.getOrDefault(d, 0);
            boolean ok = minutes >= goal;
            xp += dayXp;
            if (ok) {
                reached++;
            }
            days.add(new ProfileMetricsResponse.Day(d, dayXp, minutes, ok));
        }

        long signs =
                userLearnedSignRepository.countDistinctLearnedBetween(
                        user.getId(),
                        from.atStartOfDay(ArgentinaTime.ZONE).toInstant(),
                        to.plusDays(1).atStartOfDay(ArgentinaTime.ZONE).toInstant());

        return new ProfileMetricsResponse(
                from, to, (int) totalDays, xp, (int) signs, reached, goal, days);
    }
}
