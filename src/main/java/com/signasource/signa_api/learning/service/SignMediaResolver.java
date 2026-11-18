package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.content.service.SignCatalogImporter;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Finds a sign's file straight in the bucket, for signs the catalog has no usable key for (not
 * registered, or registered while the file did not exist yet). Same rules as the catalog importer:
 * {@code .glb}, else {@code .mp4} (+ {@code _lateral.mp4}), under the sign's name or its file-name
 * alias, ignoring case and accents. So a sign whose file is in R2 is found whether or not anyone
 * ran the import.
 *
 * <p>The bucket listing is kept for {@link #TTL} and every answer (including "nothing there") too,
 * so a lesson full of unknown signs costs one listing per few minutes, not a request per sign.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignMediaResolver {

    /** A sign's front file and, for a video, its optional side view. */
    public record Resolved(String key, String lateralKey) {}

    static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "lsa/";

    private record Cached(Optional<Resolved> value, Instant at) {}

    private record Index(Map<String, String> keys, Instant at) {}

    private final SignMediaStorage storage;
    private final Map<String, Cached> answers = new ConcurrentHashMap<>();
    private volatile Index index;

    public Optional<Resolved> resolve(String meaning) {
        String cacheKey = meaning.strip().toLowerCase(Locale.ROOT);
        Cached cached = answers.get(cacheKey);
        if (cached != null && fresh(cached.at())) {
            return cached.value();
        }
        Optional<Resolved> found = lookup(meaning.strip());
        answers.put(cacheKey, new Cached(found, Instant.now()));
        return found;
    }

    private Optional<Resolved> lookup(String meaning) {
        List<String> names = List.of(meaning, SignCatalogImporter.fileName(meaning));
        for (String name : names) {
            String glb = find(name, ".glb");
            if (glb != null) {
                return Optional.of(new Resolved(glb, null));
            }
            String mp4 = find(name, ".mp4");
            if (mp4 != null) {
                return Optional.of(new Resolved(mp4, find(name, "_lateral.mp4")));
            }
        }
        return Optional.empty();
    }

    /** The bucket's real key for {@code name + suffix}, or null. */
    private String find(String name, String suffix) {
        String found = keys().get(SignCatalogImporter.normalize(name) + "|" + suffix);
        if (found != null) {
            return found;
        }
        // Storage that cannot be listed (local/demo): ask for the exact key.
        String exact = PREFIX + name + suffix;
        try {
            return storage.exists(exact) ? exact : null;
        } catch (RuntimeException e) {
            log.warn("Could not check {}: {}", exact, e.toString());
            return null;
        }
    }

    private Map<String, String> keys() {
        Index current = index;
        if (current != null && fresh(current.at())) {
            return current.keys();
        }
        Map<String, String> keys = new HashMap<>();
        try {
            for (String key : storage.listKeys(PREFIX)) {
                String name = key.substring(PREFIX.length());
                if (name.contains("/")) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                String suffix;
                if (lower.endsWith("_lateral.mp4")) {
                    suffix = "_lateral.mp4";
                } else if (lower.endsWith(".mp4")) {
                    suffix = ".mp4";
                } else if (lower.endsWith(".glb")) {
                    suffix = ".glb";
                } else {
                    continue;
                }
                String stem = name.substring(0, name.length() - suffix.length());
                keys.putIfAbsent(SignCatalogImporter.normalize(stem) + "|" + suffix, key);
            }
        } catch (RuntimeException e) {
            log.warn("Could not list '{}': {}", PREFIX, e.toString());
        }
        index = new Index(keys, Instant.now());
        return keys;
    }

    private static boolean fresh(Instant at) {
        return at.plus(TTL).isAfter(Instant.now());
    }
}
