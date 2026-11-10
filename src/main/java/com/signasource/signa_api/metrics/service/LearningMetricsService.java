package com.signasource.signa_api.metrics.service;

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
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.SignDifficulty;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonAccuracy;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonDropoff;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonFlow;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.MeaningCount;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.ReportsSection;
import com.signasource.signa_api.metrics.repository.LearningMetricsRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final LearningMetricsRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LearningMetricsResponse getLearningMetrics(MetricsRange range) {
        Instant from = range.start();
        Instant until = range.end();

        List<Object[]> exerciseRows = repository.exerciseStats(from, until);

        return new LearningMetricsResponse(
                courses(until),
                toCountItems(repository.enrollmentsByStatus(until)),
                hardestLessons(from, until),
                repository.countLearnedSignsBetween(from, until),
                toDailyCounts(repository.dailyLearnedSigns(from, until)),
                reports(),
                toCountItems(repository.importsByStatus(from, until)),
                lessonFlow(from, until),
                exerciseFlow(exerciseRows),
                onboardingFunnel(until),
                lessonRanking(until),
                highestErrorExercises(exerciseRows),
                mostRetriedExercises(exerciseRows),
                dailyAccuracy(from, until),
                hardestSigns(exerciseRows));
    }

    private LessonFlow lessonFlow(Instant from, Instant until) {
        long started = repository.countLessonsStartedBetween(from, until);
        long completed = repository.countLessonsCompletedBetween(from, until);
        return new LessonFlow(started, completed, percent(completed, started));
    }

    /**
     * Headline exercise figures from the per-block rows: success and attempts per (user, exercise).
     */
    private ExerciseFlow exerciseFlow(List<Object[]> rows) {
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
        return new ExerciseFlow(attempted, correct, percent(correct, attempted), averageAttempts);
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

    private List<LessonDropoff> lessonRanking(Instant until) {
        List<LessonDropoff> ranking = new ArrayList<>();
        for (Object[] row : repository.lessonDropoffStats(until)) {
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
     * Extracts a human-readable sign label from the block's JSON config based on its type.
     * Returns {@code null} when the config is absent, unparseable, or the type has no sign field.
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
        } catch (Exception e) {
            return null;
        }
    }

    private List<DailyAccuracy> dailyAccuracy(Instant from, Instant until) {
        List<DailyAccuracy> series = new ArrayList<>();
        for (Object[] row : repository.dailyAccuracy(from, until)) {
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

    private List<LessonAccuracy> hardestLessons(Instant from, Instant until) {
        List<LessonAccuracy> lessons = new ArrayList<>();
        for (Object[] row : repository.lessonAccuracy(from, until, HARDEST_MIN_EVALUATED)) {
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

    private ReportsSection reports() {
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
        return new ReportsSection(times.size(), averageHours, mostReported);
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
