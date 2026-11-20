package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.AccuracySection;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.BlockTypeAccuracy;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.CountItem;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.CoursesSection;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.DailyActivityPoint;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.ErrorsSection;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.ProgressSection;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse.UsersSection;
import com.signasource.signa_api.metrics.repository.GlobalMetricsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the global business snapshot by aggregating Postgres over an instant-precise window. */
@Service
@RequiredArgsConstructor
public class GlobalMetricsService {

    private final GlobalMetricsRepository repository;

    @Transactional(readOnly = true)
    public BusinessMetricsResponse getBusinessMetrics(MetricsRange range) {
        return getBusinessMetrics(range, List.of());
    }

    @Transactional(readOnly = true)
    public BusinessMetricsResponse getBusinessMetrics(MetricsRange range, Collection<UUID> courseIds) {
        Instant from = range.start();
        Instant until = range.end();
        boolean filtered = !courseIds.isEmpty();

        long evaluated =
                filtered
                        ? repository.countEvaluatedAttemptsByCourse(from, until, courseIds)
                        : repository.countEvaluatedAttemptsBetween(from, until);
        long correct =
                filtered
                        ? repository.countCorrectAttemptsByCourse(from, until, courseIds)
                        : repository.countCorrectAttemptsBetween(from, until);

        List<CountItem> reportsByStatus = toCountItems(repository.countSignReportsByStatus());
        long pending =
                reportsByStatus.stream()
                        .filter(c -> ReportStatus.PENDING.name().equals(c.key()))
                        .mapToLong(CountItem::count)
                        .findFirst()
                        .orElse(0L);

        return new BusinessMetricsResponse(
                new CoursesSection(
                        repository.countCourses(),
                        toCountItems(repository.countVersionsByStatus())),
                toCountItems(repository.countEnrollmentsByStatus(until)),
                new ProgressSection(
                        filtered
                                ? repository.countLessonsCompletedByCourse(from, until, courseIds)
                                : repository.countLessonsCompletedBetween(from, until),
                        filtered
                                ? repository.countTopicsCompletedByCourse(from, until, courseIds)
                                : repository.countTopicsCompletedBetween(from, until)),
                new UsersSection(
                        repository.countEnabledUsers(), repository.countActiveUsers(from, until)),
                new AccuracySection(
                        evaluated,
                        correct,
                        percent(correct, evaluated),
                        blockTypeAccuracy(from, until, filtered, courseIds)),
                new ErrorsSection(
                        reportsByStatus,
                        pending,
                        repository.countFailedImportsBetween(from, until)),
                activity(from, until, filtered, courseIds));
    }

    private List<BlockTypeAccuracy> blockTypeAccuracy(
            Instant from, Instant until, boolean filtered, Collection<UUID> courseIds) {
        List<BlockTypeAccuracy> result = new ArrayList<>();
        List<Object[]> rows =
                filtered
                        ? repository.accuracyByBlockTypeByCourse(from, until, courseIds)
                        : repository.accuracyByBlockType(from, until);
        for (Object[] row : rows) {
            long evaluated = toLong(row[1]);
            long correct = toLong(row[2]);
            result.add(
                    new BlockTypeAccuracy(
                            String.valueOf(row[0]),
                            evaluated,
                            correct,
                            percent(correct, evaluated)));
        }
        return result;
    }

    /** Merges the two per-day queries (attempts and lessons completed) into one sorted series. */
    private List<DailyActivityPoint> activity(
            Instant from, Instant until, boolean filtered, Collection<UUID> courseIds) {
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        List<Object[]> attempts =
                filtered
                        ? repository.dailyAttemptsByCourse(from, until, courseIds)
                        : repository.dailyAttempts(from, until);
        for (Object[] row : attempts) {
            long[] slot = byDay.computeIfAbsent((LocalDate) row[0], k -> new long[3]);
            slot[0] = toLong(row[1]);
            slot[1] = toLong(row[2]);
        }
        List<Object[]> lessons =
                filtered
                        ? repository.dailyLessonsCompletedByCourse(from, until, courseIds)
                        : repository.dailyLessonsCompleted(from, until);
        for (Object[] row : lessons) {
            long[] slot = byDay.computeIfAbsent((LocalDate) row[0], k -> new long[3]);
            slot[2] = toLong(row[1]);
        }
        List<DailyActivityPoint> series = new ArrayList<>(byDay.size());
        byDay.forEach(
                (day, slot) -> series.add(new DailyActivityPoint(day, slot[0], slot[1], slot[2])));
        return series;
    }

    private static List<CountItem> toCountItems(List<Object[]> rows) {
        List<CountItem> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new CountItem(String.valueOf(row[0]), toLong(row[1])));
        }
        return items;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round((part * 1000.0) / whole) / 10.0;
    }
}
