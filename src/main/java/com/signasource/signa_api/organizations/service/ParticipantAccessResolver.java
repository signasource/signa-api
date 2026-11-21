package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which courses of the organization each participant takes, for the panel's figures.
 *
 * <p>A seat accepted in a <b>paid</b> purchase grants the course, even after the access expired or
 * the person was removed (their progress is still the organization's to see). A seat whose purchase
 * was refunded grants nothing: an accepted seat can only be refunded by Mercado Pago (a refund made
 * there or a chargeback), and then the course leaves that person's figures, activity included.
 * Someone the organization enrolled before seats existed has no seat at all and takes the course by
 * that enrollment.
 */
@Component
@RequiredArgsConstructor
public class ParticipantAccessResolver {

    private final PurchaseSeatRepository seatRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;

    /** Per user, per course among {@code courseIds}: their access. Users with none are absent. */
    public Map<UUID, Map<UUID, CourseAccess>> resolve(
            UUID organizationId, Collection<UUID> userIds, Collection<UUID> courseIds) {
        if (userIds.isEmpty() || courseIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> courses = new HashSet<>(courseIds);
        Map<UUID, Map<UUID, List<PurchaseSeat>>> seats = new HashMap<>();
        for (PurchaseSeat seat :
                seatRepository.findAcceptedByOrganizationIdAndUserIdIn(organizationId, userIds)) {
            UUID courseId = seat.getPurchase().getCourse().getId();
            if (courses.contains(courseId)) {
                seats.computeIfAbsent(seat.getUser().getId(), u -> new HashMap<>())
                        .computeIfAbsent(courseId, c -> new ArrayList<>())
                        .add(seat);
            }
        }

        Map<UUID, Map<UUID, CourseAccess>> accesses = new LinkedHashMap<>();
        seats.forEach(
                (userId, byCourse) ->
                        byCourse.forEach(
                                (courseId, list) -> {
                                    List<PurchaseSeat> paid =
                                            list.stream()
                                                    .filter(
                                                            s ->
                                                                    s.getPurchase().getStatus()
                                                                            == PurchaseStatus.PAID)
                                                    .toList();
                                    if (!paid.isEmpty()) {
                                        accesses.computeIfAbsent(userId, u -> new LinkedHashMap<>())
                                                .put(courseId, fromSeats(courseId, paid));
                                    }
                                }));

        for (UserCourseEnrollment enrollment :
                enrollmentRepository.findOrganizationEnrollments(
                        organizationId, userIds, courseIds)) {
            UUID userId = enrollment.getUser().getId();
            UUID courseId = enrollment.getCourseVersion().getCourse().getId();
            if (seats.getOrDefault(userId, Map.of()).containsKey(courseId)) {
                continue;
            }
            accesses.computeIfAbsent(userId, u -> new LinkedHashMap<>())
                    .merge(
                            courseId,
                            new CourseAccess(
                                    courseId,
                                    enrollment.getStartedAt(),
                                    enrollment.getAccessExpiresAt(),
                                    enrollment.getStatus() == EnrollmentStatus.DROPPED),
                            CourseAccess::merge);
        }
        return accesses;
    }

    private static CourseAccess fromSeats(UUID courseId, List<PurchaseSeat> seats) {
        return new CourseAccess(
                courseId,
                latest(seats.stream().map(PurchaseSeat::getActivatedAt)),
                latest(seats.stream().map(PurchaseSeat::getAccessExpiresAt)),
                seats.stream().allMatch(s -> s.getStatus() == SeatStatus.REVOKED));
    }

    /** The latest of the instants; {@code null} when any is null (an access with no end). */
    private static Instant latest(Stream<Instant> instants) {
        List<Instant> all = instants.toList();
        if (all.isEmpty() || all.contains(null)) {
            return null;
        }
        return all.stream().max(Comparator.naturalOrder()).orElse(null);
    }
}
