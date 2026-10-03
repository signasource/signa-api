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
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.PracticeAttemptRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.util.BlockSignExtractor;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final int MAX_LIMIT = 20;

    /**
     * The learned-signs list is a catalog, not a batch of exercises: capping it at {@link
     * #MAX_LIMIT} hid every sign past the 20th even though the course teaches many more.
     */
    private static final int MAX_SIGNS_LIMIT = 200;

    /**
     * Blocks the practice player can't evaluate: no answer to grade (INFO, INTRODUCE_SIGN,
     * INVISIBLE_SIGNS) or camera-driven (PERFORM_SIGN, SPELL_NAME), which the mobile player doesn't
     * render. Returning them used to leave the session stuck on a blank screen.
     */
    private static final Set<BlockType> NOT_PRACTICABLE =
            EnumSet.of(
                    BlockType.INFO,
                    BlockType.INTRODUCE_SIGN,
                    BlockType.INVISIBLE_SIGNS,
                    BlockType.PERFORM_SIGN,
                    BlockType.SPELL_NAME);

    /**
     * Flat bonus for finishing a mistake-review batch — unlike other practice modes, "Repaso de
     * errores" grants real XP (see PracticeController#completeMistakeReview) since it always works
     * off the user's actual pending mistakes, which shrink as they're resolved.
     */
    private static final int MISTAKE_REVIEW_XP_REWARD = 20;

    private final LessonBlockRepository lessonBlockRepository;
    private final LessonBlockAttemptRepository lessonBlockAttemptRepository;
    private final PracticeAttemptRepository practiceAttemptRepository;
    private final UserCourseEnrollmentRepository userCourseEnrollmentRepository;
    private final UserLessonProgressRepository userLessonProgressRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final UserStatsRepository userStatsRepository;
    private final BlockSignExtractor blockSignExtractor;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<LessonBlockResponse> getExercisesByType(User user, BlockType type, int limit) {
        if (NOT_PRACTICABLE.contains(type)) {
            throw new InvalidInputException("Block type is not practicable: " + type);
        }
        List<LessonBlock> blocks =
                practiceBlocks(user).stream().filter(b -> b.getType() == type).toList();
        return shuffleAndLimit(blocks, limit).stream().map(LessonBlockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LearnedSignResponse> getLearnedSigns(User user, int limit) {
        List<UserLearnedSign> learned =
                userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        user, PageRequest.of(0, Math.max(1, Math.min(limit, MAX_SIGNS_LIMIT))));

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
                practiceBlocks(user).stream()
                        .filter(b -> !NOT_PRACTICABLE.contains(b.getType()))
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

    /**
     * Grants {@link #MISTAKE_REVIEW_XP_REWARD} only if the user resolved at least one mistake
     * (answered right a block they'd previously missed) since the last time they claimed it.
     * Answering wrong on purpose leaves nothing resolved, so the reward can't be farmed that way.
     *
     * @return the XP awarded, 0 when there was nothing new to reward
     */
    @Transactional
    public int completeMistakeReview(User user) {
        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseGet(
                                () ->
                                        UserStats.builder()
                                                .user(user)
                                                .updatedAt(Instant.now())
                                                .build());

        if (!hasResolvedMistakeSince(user, stats.getLastMistakeReviewAt())) {
            return 0;
        }

        stats.setLastMistakeReviewAt(Instant.now());
        userStatsRepository.save(stats);
        eventPublisher.publishEvent(new XpEarnedEvent(this, user, MISTAKE_REVIEW_XP_REWARD));
        return MISTAKE_REVIEW_XP_REWARD;
    }

    @Transactional(readOnly = true)
    public PracticeSummaryResponse getSummary(User user) {
        int signsLearnedCount =
                userStatsRepository.findByUser(user).map(UserStats::getLearnedSignsCount).orElse(0);
        // Exercises done in lessons count too: they're exercises the user did, wherever.
        long exercisesDoneCount =
                practiceAttemptRepository.countByUserId(user.getId())
                        + lessonBlockAttemptRepository.countByUserIdAndIsCorrectIsNotNull(
                                user.getId());
        return new PracticeSummaryResponse(signsLearnedCount, exercisesDoneCount);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Blocks the user can practice: those of enrolled courses, restricted to lessons they have
     * already started. Practice is a review ("repasá lo que ya aprendiste"), so it must not serve
     * exercises — or their answers — from lessons the user hasn't reached.
     */
    private List<LessonBlock> practiceBlocks(User user) {
        List<UUID> versionIds =
                userCourseEnrollmentRepository.findByUserId(user.getId()).stream()
                        .map(enrollment -> enrollment.getCourseVersion().getId())
                        .toList();
        if (versionIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> startedLessonIds =
                userLessonProgressRepository.findByUserId(user.getId()).stream()
                        .map(progress -> progress.getLesson().getId())
                        .collect(Collectors.toSet());
        return lessonBlockRepository.findByLessonTopicCourseVersionIdIn(versionIds).stream()
                .filter(block -> startedLessonIds.contains(block.getLesson().getId()))
                .toList();
    }

    /**
     * True when the user got right, since {@code since} (null = ever), a block they had previously
     * got wrong in either a lesson or a practice — i.e. they actually resolved a mistake.
     */
    private boolean hasResolvedMistakeSince(User user, Instant since) {
        Map<UUID, Instant> firstWrongAt = new HashMap<>();
        for (LessonBlockAttempt attempt :
                lessonBlockAttemptRepository.findByUserIdOrderByAttemptedAtDesc(user.getId())) {
            if (Boolean.FALSE.equals(attempt.getIsCorrect())) {
                firstWrongAt.merge(
                        attempt.getLessonBlock().getId(),
                        attempt.getAttemptedAt(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        List<PracticeAttempt> practiceAttempts =
                practiceAttemptRepository.findByUserIdOrderByAttemptedAtDesc(user.getId());
        for (PracticeAttempt attempt : practiceAttempts) {
            if (!attempt.isCorrect()) {
                firstWrongAt.merge(
                        attempt.getLessonBlock().getId(),
                        attempt.getAttemptedAt(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return practiceAttempts.stream()
                .filter(PracticeAttempt::isCorrect)
                .filter(a -> since == null || a.getAttemptedAt().isAfter(since))
                .anyMatch(
                        a -> {
                            Instant wrongAt = firstWrongAt.get(a.getLessonBlock().getId());
                            return wrongAt != null && wrongAt.isBefore(a.getAttemptedAt());
                        });
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
            if (NOT_PRACTICABLE.contains(block.getType())) {
                continue; // e.g. a skipped camera exercise: can't be replayed in practice.
            }
            blocksById.putIfAbsent(block.getId(), block);
            attemptsByBlockId
                    .computeIfAbsent(block.getId(), k -> new ArrayList<>())
                    .add(new AttemptView(attempt.getIsCorrect(), attempt.getAttemptedAt()));
        }

        for (PracticeAttempt attempt :
                practiceAttemptRepository.findByUserIdOrderByAttemptedAtDesc(user.getId())) {
            LessonBlock block = attempt.getLessonBlock();
            if (NOT_PRACTICABLE.contains(block.getType())) {
                continue;
            }
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
