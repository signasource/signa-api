package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.dto.LearnedSignResponse;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.PracticeMistakeResponse;
import com.signasource.signa_api.learning.dto.PracticeSummaryResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.PracticeAttempt;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.PracticeAttemptRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.util.BlockSignExtractor;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final int MAX_LIMIT = 20;
    private static final Set<BlockType> NOT_PRACTICABLE =
            EnumSet.of(BlockType.INFO, BlockType.INTRODUCE_SIGN, BlockType.INVISIBLE_SIGNS);

    private final LessonBlockRepository lessonBlockRepository;
    private final LessonBlockAttemptRepository lessonBlockAttemptRepository;
    private final PracticeAttemptRepository practiceAttemptRepository;
    private final UserCourseEnrollmentRepository userCourseEnrollmentRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final UserStatsRepository userStatsRepository;
    private final BlockSignExtractor blockSignExtractor;

    @Transactional(readOnly = true)
    public List<LessonBlockResponse> getExercisesByType(User user, BlockType type, int limit) {
        if (NOT_PRACTICABLE.contains(type)) {
            throw new InvalidInputException("Block type is not practicable: " + type);
        }
        List<LessonBlock> blocks =
                enrolledBlocks(user).stream().filter(b -> b.getType() == type).toList();
        return shuffleAndLimit(blocks, limit).stream().map(LessonBlockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LearnedSignResponse> getLearnedSigns(User user, int limit) {
        List<UserLearnedSign> learned =
                userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        user, PageRequest.of(0, clamp(limit)));

        // A sign learned via more than one course version appears once, keeping the
        // desc-by-date order already returned by the query.
        return learned.stream()
                .map(UserLearnedSign::getSign)
                .distinct()
                .map(LearnedSignResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonBlockResponse> getExercisesForSign(User user, String meaning, int limit) {
        List<LessonBlock> blocks =
                enrolledBlocks(user).stream()
                        .filter(
                                b ->
                                        blockSignExtractor.extract(b).stream()
                                                .anyMatch(s -> s.equalsIgnoreCase(meaning)))
                        .toList();
        return shuffleAndLimit(blocks, limit).stream().map(LessonBlockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PracticeMistakeResponse> getMistakes(User user, int limit) {
        return mistakeBlocks(user, limit).stream()
                .map(e -> PracticeMistakeResponse.from(e.getKey(), e.getValue().misses()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonBlockResponse> getMistakeExercises(User user, int limit) {
        return mistakeBlocks(user, limit).stream()
                .map(e -> LessonBlockResponse.from(e.getKey()))
                .toList();
    }

    @Transactional
    public void recordAttempt(User user, UUID lessonBlockId, boolean isCorrect) {
        LessonBlock block =
                lessonBlockRepository
                        .findById(lessonBlockId)
                        .orElseThrow(() -> new NotFoundException("Lesson block not found"));

        practiceAttemptRepository.save(
                PracticeAttempt.builder()
                        .user(user)
                        .lessonBlock(block)
                        .isCorrect(isCorrect)
                        .build());
    }

    @Transactional(readOnly = true)
    public PracticeSummaryResponse getSummary(User user) {
        int signsLearnedCount =
                userStatsRepository.findByUser(user).map(UserStats::getLearnedSignsCount).orElse(0);
        long exercisesDoneCount = practiceAttemptRepository.countByUserId(user.getId());
        return new PracticeSummaryResponse(signsLearnedCount, exercisesDoneCount);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<LessonBlock> enrolledBlocks(User user) {
        List<UUID> versionIds =
                userCourseEnrollmentRepository.findByUserId(user.getId()).stream()
                        .map(enrollment -> enrollment.getCourseVersion().getId())
                        .toList();
        if (versionIds.isEmpty()) {
            return List.of();
        }
        return lessonBlockRepository.findByLessonTopicCourseVersionIdIn(versionIds);
    }

    private List<LessonBlock> shuffleAndLimit(List<LessonBlock> blocks, int limit) {
        List<LessonBlock> shuffled = new ArrayList<>(blocks);
        Collections.shuffle(shuffled);
        int cap = clamp(limit);
        return shuffled.subList(0, Math.min(cap, shuffled.size()));
    }

    /**
     * For each lesson block the user has ever attempted (lesson or practice), keeps it only if the
     * most recent attempt across both sources was wrong — i.e. still an unresolved mistake. Sorted
     * by that most-recent-attempt timestamp, most recent first, and already limited.
     */
    private List<Map.Entry<LessonBlock, MistakeInfo>> mistakeBlocks(User user, int limit) {
        Map<UUID, LessonBlock> blocksById = new LinkedHashMap<>();
        Map<UUID, List<AttemptView>> attemptsByBlockId = new LinkedHashMap<>();

        for (LessonBlockAttempt attempt :
                lessonBlockAttemptRepository.findByUserIdOrderByAttemptedAtDesc(user.getId())) {
            if (attempt.getIsCorrect() == null) {
                continue; // INFO block view, not an evaluable exercise attempt.
            }
            LessonBlock block = attempt.getLessonBlock();
            blocksById.putIfAbsent(block.getId(), block);
            attemptsByBlockId
                    .computeIfAbsent(block.getId(), k -> new ArrayList<>())
                    .add(new AttemptView(attempt.getIsCorrect(), attempt.getAttemptedAt()));
        }

        for (PracticeAttempt attempt :
                practiceAttemptRepository.findByUserIdOrderByAttemptedAtDesc(user.getId())) {
            LessonBlock block = attempt.getLessonBlock();
            blocksById.putIfAbsent(block.getId(), block);
            attemptsByBlockId
                    .computeIfAbsent(block.getId(), k -> new ArrayList<>())
                    .add(new AttemptView(attempt.isCorrect(), attempt.getAttemptedAt()));
        }

        Map<LessonBlock, MistakeInfo> mistakes = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<AttemptView>> entry : attemptsByBlockId.entrySet()) {
            List<AttemptView> attempts = entry.getValue();
            AttemptView latest =
                    attempts.stream()
                            .max(Comparator.comparing(AttemptView::attemptedAt))
                            .orElseThrow();
            if (latest.isCorrect()) {
                continue;
            }
            long misses = attempts.stream().filter(a -> !a.isCorrect()).count();
            mistakes.put(
                    blocksById.get(entry.getKey()),
                    new MistakeInfo((int) misses, latest.attemptedAt()));
        }

        return mistakes.entrySet().stream()
                .sorted(
                        Comparator.comparing(
                                        (Map.Entry<LessonBlock, MistakeInfo> e) ->
                                                e.getValue().lastAttemptedAt())
                                .reversed())
                .limit(clamp(limit))
                .toList();
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private record AttemptView(boolean isCorrect, Instant attemptedAt) {}

    private record MistakeInfo(int misses, Instant lastAttemptedAt) {}
}
