package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.gamification.dto.UserStatsResponse;
import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserDailyXpRepository userDailyXpRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional(readOnly = true)
    public UserStatsResponse getStats(User user) {
        return userStatsRepository
                .findByUser(user)
                .map(UserStatsResponse::from)
                .orElseThrow(() -> new NotFoundException("Stats not found for user"));
    }

    @Transactional(readOnly = true)
    public List<DailyXpResponse> getWeeklyXpBreakdown(User user) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<UserDailyXp> records =
                userDailyXpRepository.findByUserAndXpDateBetween(user, monday, today);

        Map<LocalDate, Integer> xpByDate =
                records.stream()
                        .collect(
                                Collectors.toMap(UserDailyXp::getXpDate, UserDailyXp::getXpEarned));

        List<DailyXpResponse> result = new ArrayList<>();
        for (LocalDate day = monday; !day.isAfter(today); day = day.plusDays(1)) {
            result.add(new DailyXpResponse(day, xpByDate.getOrDefault(day, 0)));
        }
        return result;
    }
}
