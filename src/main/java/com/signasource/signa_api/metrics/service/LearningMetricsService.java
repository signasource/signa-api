package com.signasource.signa_api.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.CountItem;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.CourseStat;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.DailyAccuracy;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.DailyCount;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.ExerciseDifficulty;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.ExerciseFlow;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonAccuracy;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonDropoff;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonFlow;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonReview;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.MeaningCount;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.ReportsSection;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.SignDifficulty;
import com.signasource.signa_api.metrics.repository.LearningMetricsRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the global learning & content snapshot by aggregating Postgres over an instant window. */
@Service
@RequiredArgsConstructor
public class LearningMetricsService {

    /** Minimum evaluated attempts for a lesson to be ranked among the hardest (noise floor). */
    private static final long HARDEST_MIN_EVALUATED = 1;

    private static final int HARDEST_LIMIT = 10;
    private static final int MOST_REPORTED_LIMIT = 10;

    /** Noise floors so a ranking isn't dominated by one-off attempts / starts. */
    private static final long EXERCISE_MIN_EVALUATED = 3;

    private static final long LESSON_MIN_STARTED = 2;
    private static final int RANKING_LIMIT = 10;

    /** "Contenido a revisar": a lesson needs this many exercise attempts in the window to rank. */
    static final long REVIEW_MIN_ATTEMPTS = 30;

    private static final int REVIEW_LIMIT = 10;

    private final LearningMetricsRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LearningMetricsResponse getLearningMetrics(MetricsRange range) {
        return getLearningMetrics(range, List.of());
    }

    @Transactional(readOnly = true)
    public LearningMetricsResponse getLearningMetrics(
            MetricsRange range, Collection<UUID> courseIds) {
        Instant from = range.start();
        Instant until = range.end();
        boolean filtered = !courseIds.isEmpty();
        // Immediately preceding equal-length window for "vs. período anterior"; absent (null) for
        // the
        // whole history, which has no comparable prior span.
        Instant previousFrom = range.isOpen() ? null : from.minus(Duration.between(from, until));

        List<Object[]> exerciseRows =
                filtered
                        ? repository.exerciseStatsByCourse(from, until, courseIds)
                        : repository.exerciseStats(from, until);
        List<Object[]> previousExerciseRows =
                previousFrom == null
                        ? null
                        : (filtered
                                ? repository.exerciseStatsByCourse(previousFrom, from, courseIds)
                                : repository.exerciseStats(previousFrom, from));

        List<Object[]> pendingReports = repository.pendingReportsBySign();

        return new LearningMetricsResponse(
                courses(until),
                toCountItems(repository.enrollmentsByStatus(until)),
                hardestLessons(from, until, filtered, courseIds),
                repository.countLearnedSignsBetween(from, until),
                toDailyCounts(repository.dailyLearnedSigns(from, until)),
                reports(pendingReports),
                toCountItems(repository.importsByStatus(from, until)),
                lessonFlow(from, until, previousFrom, filtered, courseIds),
                exerciseFlow(exerciseRows, previousExerciseRows),
                onboardingFunnel(until),
                lessonRanking(until, filtered, courseIds),
                highestErrorExercises(exerciseRows),
                mostRetriedExercises(exerciseRows),
                dailyAccuracy(from, until, filtered, courseIds),
                hardestSigns(exerciseRows),
                lessonsToReview(from, until, filtered, courseIds, pendingReports));
    }

    /**
     * Lessons with at least {@link #REVIEW_MIN_ATTEMPTS} exercise attempts in the window, ranked by
     * abandonment (worst first), each with its course, accuracy and the pending reports on signs it
     * uses. A report counts toward a lesson when its sign's meaning appears (case-insensitive) as a
     * value in one of the lesson's block configs — blocks reference signs by meaning.
     */
    private List<LessonReview> lessonsToReview(
            Instant from,
            Instant until,
            boolean filtered,
            Collection<UUID> courseIds,
            List<Object[]> pendingReports) {
        List<Object[]> accuracyRows =
                filtered
                        ? repository.lessonAccuracyByCourse(
                                from, until, REVIEW_MIN_ATTEMPTS, courseIds)
                        : repository.lessonAccuracy(from, until, REVIEW_MIN_ATTEMPTS);
        Map<String, long[]> accuracy = new HashMap<>();
        for (Object[] row : accuracyRows) {
            accuracy.put(String.valueOf(row[0]), new long[] {toLong(row[2]), toLong(row[3])});
        }
        if (accuracy.isEmpty()) {
            return List.of();
        }

        List<Object[]> progressRows =
                filtered
                        ? repository.lessonReviewStatsByCourse(until, courseIds)
                        : repository.lessonReviewStats(until);
        List<Object[]> candidates = new ArrayList<>();
        for (Object[] row : progressRows) {
            if (accuracy.containsKey(String.valueOf(row[0]))
                    && toLong(row[4]) >= LESSON_MIN_STARTED) {
                candidates.add(row);
            }
        }
        candidates.sort(
                Comparator.comparingDouble((Object[] row) -> abandonment(row))
                        .reversed()
                        .thenComparing(
                                row -> accuracy.get(String.valueOf(row[0]))[0],
                                Comparator.reverseOrder()));
        if (candidates.size() > REVIEW_LIMIT) {
            candidates = candidates.subList(0, REVIEW_LIMIT);
        }

        Map<String, Long> pendingByMeaning = new HashMap<>();
        for (Object[] row : pendingReports) {
            if (row[0] != null) {
                pendingByMeaning.merge(
                        normalize(String.valueOf(row[0])), toLong(row[1]), Long::sum);
            }
        }
        Map<String, Set<String>> lessonTerms = lessonTerms(candidates, pendingByMeaning);

        List<LessonReview> reviews = new ArrayList<>(candidates.size());
        for (Object[] row : candidates) {
            String lessonId = String.valueOf(row[0]);
            long[] acc = accuracy.get(lessonId);
            long reports = 0;
            for (String term : lessonTerms.getOrDefault(lessonId, Set.of())) {
                reports += pendingByMeaning.getOrDefault(term, 0L);
            }
            reviews.add(
                    new LessonReview(
                            lessonId,
                            String.valueOf(row[1]),
                            String.valueOf(row[2]),
                            String.valueOf(row[3]),
                            toLong(row[4]),
                            toLong(row[5]),
                            abandonment(row),
                            acc[0],
                            percent(acc[1], acc[0]),
                            reports));
        }
        return reviews;
    }

    /** Abandonment of a {@code lessonReviewStats} row: started but not completed, in percent. */
    private static double abandonment(Object[] row) {
        long started = toLong(row[4]);
        long completed = toLong(row[5]);
        return Math.round((100.0 - percent(completed, started)) * 10.0) / 10.0;
    }

    /** The normalized text values of each lesson's block configs that match a reported sign. */
    private Map<String, Set<String>> lessonTerms(
            List<Object[]> lessons, Map<String, Long> pendingByMeaning) {
        if (lessons.isEmpty() || pendingByMeaning.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = new ArrayList<>(lessons.size());
        for (Object[] row : lessons) {
            ids.add(row[0] instanceof UUID id ? id : UUID.fromString(String.valueOf(row[0])));
        }
        Map<String, Set<String>> terms = new HashMap<>();
        for (Object[] row : repository.blockConfigsOfLessons(ids)) {
            if (row[1] == null) {
                continue;
            }
            Set<String> found = terms.computeIfAbsent(String.valueOf(row[0]), k -> new HashSet<>());
            try {
                collectText(objectMapper.readTree(String.valueOf(row[1])), found, pendingByMeaning);
            } catch (JsonProcessingException | RuntimeException e) {
                // An unparseable config just contributes no reports.
            }
        }
        return terms;
    }

    private static void collectText(JsonNode node, Set<String> out, Map<String, Long> wanted) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            String term = normalize(node.asText());
            if (wanted.containsKey(term)) {
                out.add(term);
            }
            return;
        }
        for (JsonNode child : node) {
            collectText(child, out, wanted);
        }
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private LessonFlow lessonFlow(
            Instant from,
            Instant until,
            Instant previousFrom,
            boolean filtered,
            Collection<UUID> courseIds) {
        long started = countLessonsStarted(from, until, filtered, courseIds);
        long completed = countLessonsCompleted(from, until, filtered, courseIds);
        Long previousStarted =
                previousFrom == null
                        ? null
                        : countLessonsStarted(previousFrom, from, filtered, courseIds);
        Long previousCompleted =
                previousFrom == null
                        ? null
                        : countLessonsCompleted(previousFrom, from, filtered, courseIds);
        return new LessonFlow(
                started,
                completed,
                percent(completed, started),
                previousStarted,
                previousCompleted);
    }

    private long countLessonsStarted(
            Instant from, Instant until, boolean filtered, Collection<UUID> courseIds) {
        return filtered
                ? repository.countLessonsStartedByCourse(from, until, courseIds)
                : repository.countLessonsStartedBetween(from, until);
    }

    private long countLessonsCompleted(
            Instant from, Instant until, boolean filtered, Collection<UUID> courseIds) {
        return filtered
                ? repository.countLessonsCompletedByCourse(from, until, courseIds)
                : repository.countLessonsCompletedBetween(from, until);
    }

    /**
     * Headline exercise figures from the per-block rows: success and attempts per (user, exercise).
     * {@code previousRows} are the same rows over the preceding window ({@code null} for the whole
     * history).
     */
    private ExerciseFlow exerciseFlow(List<Object[]> rows, List<Object[]> previousRows) {
        long attempted = 0;
        long correct = 0;
        long userExercisePairs = 0;
        for (Object[] row : rows) {
            attempted += toLong(row[3]);
            correct += toLong(row[4]);
            userExercisePairs += toLong(row[5]);
        }
        double averageAttempts =
                userExercisePairs == 0
                        ? 0.0
                        : Math.round((attempted * 10.0) / userExercisePairs) / 10.0;
        Long previousAttempted = null;
        Long previousCorrect = null;
        if (previousRows != null) {
            long pa = 0;
            long pc = 0;
            for (Object[] row : previousRows) {
                pa += toLong(row[3]);
                pc += toLong(row[4]);
            }
            previousAttempted = pa;
            previousCorrect = pc;
        }
        return new ExerciseFlow(
                attempted,
                correct,
                percent(correct, attempted),
                averageAttempts,
                previousAttempted,
                previousCorrect);
    }

    private List<CountItem> onboardingFunnel(Instant until) {
        List<CountItem> steps = new ArrayList<>(5);
        steps.add(new CountItem("Usuarios registrados", repository.countUsersBefore(until)));
        steps.add(
                new CountItem(
                        "Iniciaron una lección", repository.countUsersStartedLessonBefore(until)));
        steps.add(
                new CountItem(
                        "Hicieron un ejercicio", repository.countUsersAttemptedBefore(until)));
        steps.add(
                new CountItem(
                        "Completaron una lección",
                        repository.countUsersCompletedLessonBefore(until)));
        steps.add(
                new CountItem(
                        "Completaron 2+ lecciones",
                        repository.usersWithTwoPlusCompletedLessons(until).size()));
        return steps;
    }

    private List<LessonDropoff> lessonRanking(
            Instant until, boolean filtered, Collection<UUID> courseIds) {
        List<Object[]> rows =
                filtered
                        ? repository.lessonDropoffStatsByCourse(until, courseIds)
                        : repository.lessonDropoffStats(until);
        List<LessonDropoff> ranking = new ArrayList<>();
        for (Object[] row : rows) {
            long started = toLong(row[2]);
            if (started < LESSON_MIN_STARTED) {
                continue;
            }
            long completed = toLong(row[3]);
            double completion = percent(completed, started);
            ranking.add(
                    new LessonDropoff(
                            String.valueOf(row[0]),
                            String.valueOf(row[1]),
                            started,
                            completed,
                            completion,
                            Math.round((100.0 - completion) * 10.0) / 10.0));
        }
        ranking.sort(Comparator.comparingDouble(LessonDropoff::abandonmentPercent).reversed());
        return ranking.size() > RANKING_LIMIT ? ranking.subList(0, RANKING_LIMIT) : ranking;
    }

    private List<ExerciseDifficulty> highestErrorExercises(List<Object[]> rows) {
        List<ExerciseDifficulty> exercises = difficulties(rows);
        exercises.sort(Comparator.comparingDouble(ExerciseDifficulty::errorPercent).reversed());
        return exercises.size() > RANKING_LIMIT ? exercises.subList(0, RANKING_LIMIT) : exercises;
    }

    private List<ExerciseDifficulty> mostRetriedExercises(List<Object[]> rows) {
        List<ExerciseDifficulty> exercises = difficulties(rows);
        exercises.sort(Comparator.comparingDouble(ExerciseDifficulty::averageAttempts).reversed());
        return exercises.size() > RANKING_LIMIT ? exercises.subList(0, RANKING_LIMIT) : exercises;
    }

    /** Maps the per-block rows to difficulty items, dropping blocks below the noise floor. */
    private List<ExerciseDifficulty> difficulties(List<Object[]> rows) {
        List<ExerciseDifficulty> exercises = new ArrayList<>();
        for (Object[] row : rows) {
            long evaluated = toLong(row[3]);
            if (evaluated < EXERCISE_MIN_EVALUATED) {
                continue;
            }
            long correct = toLong(row[4]);
            long users = toLong(row[5]);
            double averageAttempts =
                    users == 0 ? 0.0 : Math.round((evaluated * 10.0) / users) / 10.0;
            BlockType type = (BlockType) row[2];
            String config = row.length > 6 && row[6] != null ? String.valueOf(row[6]) : null;
            String signLabel = extractSignFromConfig(config, type);
            String label = signLabel != null ? signLabel : (row[1] + " · " + row[2]);
            exercises.add(
                    new ExerciseDifficulty(
                            String.valueOf(row[0]),
                            label,
                            evaluated,
                            correct,
                            percent(evaluated - correct, evaluated),
                            averageAttempts));
        }
        return exercises;
    }

    /**
     * Signs ranked by error rate, derived from SELECT_MEANING and SELECT_SIGN blocks (single-sign
     * exercises). Multi-sign blocks (MATCH, VISUAL_RECOGNITION) are excluded — they don't map
     * cleanly to one sign's difficulty.
     */
    private List<SignDifficulty> hardestSigns(List<Object[]> rows) {
        Map<String, long[]> bySign = new LinkedHashMap<>();
        for (Object[] row : rows) {
            long evaluated = toLong(row[3]);
            if (evaluated < EXERCISE_MIN_EVALUATED) continue;
            BlockType type = (BlockType) row[2];
            if (type != BlockType.SELECT_MEANING && type != BlockType.SELECT_SIGN) continue;
            String config = row.length > 6 && row[6] != null ? String.valueOf(row[6]) : null;
            String sign = extractSignFromConfig(config, type);
            if (sign == null || sign.isBlank()) continue;
            long correct = toLong(row[4]);
            long users = toLong(row[5]);
            long[] slot = bySign.computeIfAbsent(sign, k -> new long[3]);
            slot[0] += evaluated;
            slot[1] += correct;
            slot[2] += users;
        }
        List<SignDifficulty> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : bySign.entrySet()) {
            long[] v = e.getValue();
            double avgAttempts = v[2] == 0 ? 0.0 : Math.round((v[0] * 10.0) / v[2]) / 10.0;
            result.add(
                    new SignDifficulty(
                            e.getKey(), v[0], v[1], percent(v[0] - v[1], v[0]), avgAttempts));
        }
        result.sort(Comparator.comparingDouble(SignDifficulty::errorPercent).reversed());
        return result.size() > HARDEST_LIMIT ? result.subList(0, HARDEST_LIMIT) : result;
    }

    /**
     * Extracts a human-readable sign label from the block's JSON config based on its type. Returns
     * {@code null} when the config is absent, unparseable, or the type has no sign field.
     */
    @SuppressWarnings("unchecked")
    private String extractSignFromConfig(String config, BlockType type) {
        if (config == null || config.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(config, Map.class);
            return switch (type) {
                case SELECT_MEANING -> (String) map.get("sign");
                case SELECT_SIGN -> (String) map.get("word");
                case CONTEXT_RESPONSE -> (String) map.get("answer");
                case MATCH -> {
                    Object concepts = map.get("concepts");
                    if (concepts instanceof List<?> list) {
                        yield list.stream().map(Object::toString).collect(Collectors.joining(", "));
                    }
                    yield null;
                }
                case VISUAL_RECOGNITION -> {
                    Object seq = map.get("signSequence");
                    if (seq instanceof List<?> list) {
                        yield list.stream().map(Object::toString).collect(Collectors.joining(", "));
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private List<DailyAccuracy> dailyAccuracy(
            Instant from, Instant until, boolean filtered, Collection<UUID> courseIds) {
        List<Object[]> rows =
                filtered
                        ? repository.dailyAccuracyByCourse(from, until, courseIds)
                        : repository.dailyAccuracy(from, until);
        List<DailyAccuracy> series = new ArrayList<>();
        for (Object[] row : rows) {
            long evaluated = toLong(row[1]);
            long correct = toLong(row[2]);
            series.add(
                    new DailyAccuracy(
                            (LocalDate) row[0], evaluated, correct, percent(correct, evaluated)));
        }
        series.sort(Comparator.comparing(DailyAccuracy::day));
        return series;
    }

    /** Folds the per-course status rows into one {@link CourseStat} each, ranked by enrollments. */
    private List<CourseStat> courses(Instant until) {
        Map<String, long[]> byCourse = new LinkedHashMap<>(); // [enrolled, completed, total]
        Map<String, String> names = new LinkedHashMap<>();
        for (Object[] row : repository.enrollmentsByCourse(until)) {
            String courseId = String.valueOf(row[0]);
            names.putIfAbsent(courseId, String.valueOf(row[1]));
            long count = toLong(row[3]);
            long[] slot = byCourse.computeIfAbsent(courseId, k -> new long[3]);
            if (row[2] == EnrollmentStatus.ENROLLED) {
                slot[0] += count;
            } else if (row[2] == EnrollmentStatus.COMPLETED) {
                slot[1] += count;
            }
            slot[2] += count;
        }
        List<CourseStat> stats = new ArrayList<>(byCourse.size());
        byCourse.forEach(
                (courseId, slot) ->
                        stats.add(
                                new CourseStat(
                                        courseId,
                                        names.get(courseId),
                                        slot[0],
                                        slot[1],
                                        slot[2],
                                        percent(slot[1], slot[2]))));
        stats.sort(Comparator.comparingLong(CourseStat::total).reversed());
        return stats;
    }

    private List<LessonAccuracy> hardestLessons(
            Instant from, Instant until, boolean filtered, Collection<UUID> courseIds) {
        List<Object[]> rows =
                filtered
                        ? repository.lessonAccuracyByCourse(
                                from, until, HARDEST_MIN_EVALUATED, courseIds)
                        : repository.lessonAccuracy(from, until, HARDEST_MIN_EVALUATED);
        List<LessonAccuracy> lessons = new ArrayList<>();
        for (Object[] row : rows) {
            long evaluated = toLong(row[2]);
            long correct = toLong(row[3]);
            lessons.add(
                    new LessonAccuracy(
                            String.valueOf(row[0]),
                            String.valueOf(row[1]),
                            evaluated,
                            correct,
                            percent(correct, evaluated)));
        }
        lessons.sort(Comparator.comparingDouble(LessonAccuracy::accuracyPercent));
        return lessons.size() > HARDEST_LIMIT ? lessons.subList(0, HARDEST_LIMIT) : lessons;
    }

    private ReportsSection reports(List<Object[]> pendingReports) {
        List<Object[]> times = repository.resolvedReportTimes();
        long totalMinutes = 0;
        for (Object[] row : times) {
            totalMinutes +=
                    Duration.between((LocalDateTime) row[0], (LocalDateTime) row[1]).toMinutes();
        }
        double averageHours =
                times.isEmpty()
                        ? 0.0
                        : Math.round((totalMinutes / (double) times.size() / 60.0) * 10.0) / 10.0;

        List<Object[]> reported = repository.mostReportedSigns();
        List<MeaningCount> mostReported = new ArrayList<>();
        for (Object[] row : reported) {
            if (mostReported.size() >= MOST_REPORTED_LIMIT) {
                break;
            }
            mostReported.add(new MeaningCount(String.valueOf(row[0]), toLong(row[1])));
        }
        List<MeaningCount> mostReportedPending = new ArrayList<>();
        for (Object[] row : pendingReports) {
            if (mostReportedPending.size() >= MOST_REPORTED_LIMIT) {
                break;
            }
            mostReportedPending.add(new MeaningCount(String.valueOf(row[0]), toLong(row[1])));
        }
        return new ReportsSection(times.size(), averageHours, mostReported, mostReportedPending);
    }

    private static List<CountItem> toCountItems(List<Object[]> rows) {
        List<CountItem> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new CountItem(String.valueOf(row[0]), toLong(row[1])));
        }
        return items;
    }

    private static List<DailyCount> toDailyCounts(List<Object[]> rows) {
        List<DailyCount> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            series.add(new DailyCount((LocalDate) row[0], toLong(row[1])));
        }
        series.sort(Comparator.comparing(DailyCount::day));
        return series;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round((part * 1000.0) / whole) / 10.0;
    }
}
