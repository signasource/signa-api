package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.CountItem;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.CourseStat;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.DailyCount;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse.LessonAccuracy;
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

    private final LearningMetricsRepository repository;

    @Transactional(readOnly = true)
    public LearningMetricsResponse getLearningMetrics(MetricsRange range) {
        Instant from = range.start();
        Instant until = range.end();

        return new LearningMetricsResponse(
                courses(until),
                toCountItems(repository.enrollmentsByStatus(until)),
                hardestLessons(from, until),
                repository.countLearnedSignsBetween(from, until),
                toDailyCounts(repository.dailyLearnedSigns(from, until)),
                reports(),
                toCountItems(repository.importsByStatus(from, until)));
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
