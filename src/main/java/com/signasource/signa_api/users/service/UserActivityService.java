package com.signasource.signa_api.users.service;

import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import com.signasource.signa_api.users.repository.UserDailyActivityRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserDailyActivityRepository userDailyActivityRepository;

    @Transactional
    public void recordActivity(User user, int minutes) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UserDailyActivity activity =
                userDailyActivityRepository
                        .findByUserAndActivityDate(user, today)
                        .orElseGet(
                                () ->
                                        UserDailyActivity.builder()
                                                .user(user)
                                                .activityDate(today)
                                                .build());
        activity.setMinutes(activity.getMinutes() + minutes);
        userDailyActivityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public int getMinutesToday(User user) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return userDailyActivityRepository
                .findByUserAndActivityDate(user, today)
                .map(UserDailyActivity::getMinutes)
                .orElse(0);
    }
}
