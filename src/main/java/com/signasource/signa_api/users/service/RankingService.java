package com.signasource.signa_api.users.service;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.dto.MyRankingPositionResponse;
import com.signasource.signa_api.users.dto.RankingEntryResponse;
import com.signasource.signa_api.users.dto.WeeklyRankingResponse;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingService {

    private static final int GLOBAL_LIMIT = 100;
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

    private final UserStatsRepository userStatsRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional(readOnly = true)
    public WeeklyRankingResponse getGlobalRanking(User currentUser) {
        List<UserStats> top = userStatsRepository.findTopByWeeklyXpDesc(PageRequest.of(0, GLOBAL_LIMIT));
        int total = (int) userStatsRepository.count();

        UserStats myStats = userStatsRepository.findByUser(currentUser).orElse(null);
        int myXp = myStats != null ? myStats.getWeeklyXp() : 0;
        long ahead = userStatsRepository.countUsersAheadByWeeklyXp(myXp, currentUser.getId());
        int myRank = (int) ahead + 1;

        List<RankingEntryResponse> entries = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            entries.add(buildEntry(i + 1, top.get(i)));
        }

        String gapText = computeGlobalGapText(myRank, myXp, top);
        Integer myDelta = computeDelta(myStats, myRank);
        var me = new MyRankingPositionResponse(myRank, myXp, myDelta, gapText);

        return new WeeklyRankingResponse(entries, total, me);
    }

    @Transactional(readOnly = true)
    public WeeklyRankingResponse getFriendsRanking(User currentUser) {
        List<Friendship> friendships = friendshipRepository.findAllFriendshipsByUserAndStatus(
                currentUser, FriendshipStatus.ACCEPTED);

        Set<UUID> participantIds = new HashSet<>();
        participantIds.add(currentUser.getId());
        for (Friendship f : friendships) {
            UUID friendId = f.getRequester().getId().equals(currentUser.getId())
                    ? f.getAddressee().getId()
                    : f.getRequester().getId();
            participantIds.add(friendId);
        }

        List<UserStats> sorted = userStatsRepository.findByUserIdInOrderByWeeklyXpDesc(participantIds);
        Map<UUID, Integer> prevFriendsRank = computePreviousFriendsRanks(sorted);

        int myRank = 1;
        int myXp = 0;
        UserStats myStats = null;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUser().getId().equals(currentUser.getId())) {
                myRank = i + 1;
                myXp = sorted.get(i).getWeeklyXp();
                myStats = sorted.get(i);
                break;
            }
        }

        List<RankingEntryResponse> entries = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            UserStats us = sorted.get(i);
            int rank = i + 1;
            Integer prevRank = prevFriendsRank.get(us.getUser().getId());
            Integer delta = (prevRank != null && us.getPreviousWeeklyRank() != null)
                    ? prevRank - rank : null;
            entries.add(buildEntryWithDelta(rank, us, delta));
        }

        String gapText = null;
        if (myRank > 1) {
            UserStats above = sorted.get(myRank - 2);
            long gap = (long) above.getWeeklyXp() - myXp;
            if (gap > 0) {
                String firstName = above.getUser().getName().split(" ")[0];
                gapText = "Te faltan " + fmtXp(gap) + " XP para pasar a " + firstName;
            }
        }

        Integer myPrevFriendsRank = prevFriendsRank.get(currentUser.getId());
        Integer myDelta = (myPrevFriendsRank != null && myStats != null && myStats.getPreviousWeeklyRank() != null)
                ? myPrevFriendsRank - myRank : null;

        var me = new MyRankingPositionResponse(myRank, myXp, myDelta, gapText);
        return new WeeklyRankingResponse(entries, participantIds.size(), me);
    }

    private RankingEntryResponse buildEntry(int rank, UserStats stats) {
        return buildEntryWithDelta(rank, stats, computeDelta(stats, rank));
    }

    private RankingEntryResponse buildEntryWithDelta(int rank, UserStats stats, Integer delta) {
        User user = stats.getUser();
        return new RankingEntryResponse(
                rank, user.getId(), user.getUsername(), user.getName(),
                stats.getWeeklyXp(), stats.getCurrentStreak(), delta);
    }

    private Integer computeDelta(UserStats stats, int currentRank) {
        if (stats == null || stats.getPreviousWeeklyRank() == null) return null;
        return stats.getPreviousWeeklyRank() - currentRank;
    }

    /** Derives last-week's friends ranking order from each participant's stored global rank. */
    private Map<UUID, Integer> computePreviousFriendsRanks(List<UserStats> stats) {
        List<UserStats> withPrev = stats.stream()
                .filter(us -> us.getPreviousWeeklyRank() != null)
                .sorted(Comparator.comparingInt(UserStats::getPreviousWeeklyRank))
                .toList();

        Map<UUID, Integer> result = new HashMap<>();
        for (int i = 0; i < withPrev.size(); i++) {
            result.put(withPrev.get(i).getUser().getId(), i + 1);
        }
        return result;
    }

    private String computeGlobalGapText(int myRank, int myXp, List<UserStats> top) {
        if (myRank == 1) return null;
        if (myRank > 10) {
            if (top.size() < 10) return null;
            long gap = (long) top.get(9).getWeeklyXp() - myXp;
            if (gap <= 0) return null;
            return "Te faltan " + fmtXp(gap) + " XP para entrar al top 10";
        }
        UserStats above = top.get(myRank - 2);
        long gap = (long) above.getWeeklyXp() - myXp;
        if (gap <= 0) return null;
        String firstName = above.getUser().getName().split(" ")[0];
        return "Te faltan " + fmtXp(gap) + " XP para pasar a " + firstName;
    }

    private static String fmtXp(long xp) {
        return NumberFormat.getIntegerInstance(ES_AR).format(xp);
    }
}
