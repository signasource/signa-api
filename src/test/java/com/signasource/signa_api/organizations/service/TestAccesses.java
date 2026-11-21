package com.signasource.signa_api.organizations.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Access maps for hand-built {@code ContentScope}s. */
final class TestAccesses {

    private TestAccesses() {}

    /** Whoever is asked about takes every one of those courses. */
    static Map<UUID, Map<UUID, CourseAccess>> everyoneTakes(List<UUID> courseIds) {
        Map<UUID, CourseAccess> all = new LinkedHashMap<>();
        courseIds.forEach(id -> all.put(id, CourseAccess.open(id)));
        return new HashMap<>() {
            @Override
            public Map<UUID, CourseAccess> getOrDefault(Object key, Map<UUID, CourseAccess> other) {
                return all;
            }

            @Override
            public Map<UUID, CourseAccess> get(Object key) {
                return all;
            }
        };
    }

    /** Each of those users takes every one of those courses; anyone else takes none. */
    static Map<UUID, Map<UUID, CourseAccess>> take(List<UUID> userIds, List<UUID> courseIds) {
        Map<UUID, Map<UUID, CourseAccess>> accesses = new HashMap<>();
        for (UUID userId : userIds) {
            Map<UUID, CourseAccess> courses = new LinkedHashMap<>();
            courseIds.forEach(id -> courses.put(id, CourseAccess.open(id)));
            accesses.put(userId, courses);
        }
        return accesses;
    }
}
