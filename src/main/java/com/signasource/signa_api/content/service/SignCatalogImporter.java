package com.signasource.signa_api.content.service;

import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.yaml.LessonDto;
import com.signasource.signa_api.content.dto.yaml.TopicYaml;
import com.signasource.signa_api.content.exception.SignLanguageNotFoundException;
import com.signasource.signa_api.content.util.SignCatalogExtractor;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.learning.service.SignMediaStorage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignCatalogImporter {

    private static final Handedness DEFAULT_HANDEDNESS = Handedness.ONE_HANDED;

    /**
     * Signs whose file in the bucket is named differently from the YAML meaning (the video is
     * {@code nene.mp4}, the lesson teaches "niño"). Keys and values are compared normalized.
     */
    private static final Map<String, String> FILE_NAME_ALIASES =
            Map.of(
                    "niño", "nene",
                    "niña", "nena",
                    "hoy", "hoy-ahora",
                    "comisaría", "policía",
                    "mañana", "mañana (día siguiente)",
                    "la mañana", "mañana (parte del día)");

    private final SignCatalogExtractor extractor;
    private final SignRepository signRepository;
    private final SignLanguageRepository signLanguageRepository;
    private final SignMediaStorage mediaStorage;

    @Transactional
    public void importSigns(List<LoadedCourse> courses) {
        List<Sign> newSigns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        MediaIndex index = new MediaIndex();

        for (LoadedCourse course : courses) {
            SignLanguage signLanguage =
                    signLanguageRepository
                            .findByCode(course.signLanguageCode())
                            .orElseThrow(
                                    () ->
                                            new SignLanguageNotFoundException(
                                                    course.signLanguageCode()));

            for (String meaning : meanings(course)) {
                if (!seen.add(meaning)) {
                    continue;
                }
                signRepository
                        .findByMeaningIgnoreCase(meaning)
                        .ifPresentOrElse(
                                existing -> {
                                    boolean meaningDrift = !existing.getMeaning().equals(meaning);
                                    boolean mediaDrift =
                                            syncMedia(existing, signLanguage, meaning, index);
                                    if (meaningDrift || mediaDrift) {
                                        existing.setMeaning(meaning);
                                        signRepository.save(existing);
                                    }
                                },
                                () -> newSigns.add(newSign(signLanguage, meaning, index)));
            }
        }

        signRepository.saveAll(newSigns);
    }

    /**
     * Re-resolves the media of every sign that still points at a default key (or has none), without
     * reading the YAML: unchanged courses are not re-imported, so this is how an existing catalog
     * gets rebuilt without resurrecting names renamed from the admin panel. Returns how many signs
     * changed.
     */
    @Transactional
    public int refreshMediaKeys() {
        int changed = 0;
        MediaIndex index = new MediaIndex();
        for (Sign sign : signRepository.findAll()) {
            SignLanguage language = sign.getSignLanguage();
            if (language != null && syncMedia(sign, language, sign.getMeaning(), index)) {
                signRepository.save(sign);
                changed++;
            }
        }
        return changed;
    }

    private Sign newSign(SignLanguage signLanguage, String meaning, MediaIndex index) {
        Sign sign =
                Sign.builder()
                        .meaning(meaning)
                        .handedness(DEFAULT_HANDEDNESS)
                        .signLanguage(signLanguage)
                        .build();
        syncMedia(sign, signLanguage, meaning, index);
        return sign;
    }

    /**
     * Points the sign at what is really in the bucket: {@code <meaning>.glb}, else {@code
     * <meaning>.mp4} (plus {@code _lateral.mp4} if present), else the bare {@code <meaning>} key
     * with no extension. Names are matched ignoring case and accents (the YAML says {@code
     * clinica}, the file is {@code Clínica.mp4}) and the sign stores the key the bucket really has.
     * A key that is not one of those defaults was uploaded from the panel and is left alone.
     * Returns whether anything changed.
     */
    private boolean syncMedia(
            Sign sign, SignLanguage signLanguage, String meaning, MediaIndex index) {
        String key = sign.getAnimationUrl();
        if (!isDefaultKey(key, signLanguage, meaning)) {
            return false;
        }
        String resolved = index.find(signLanguage, meaning, ".glb");
        String lateral = null;
        if (resolved == null) {
            resolved = index.find(signLanguage, meaning, ".mp4");
            if (resolved != null) {
                lateral = index.find(signLanguage, meaning, "_lateral.mp4");
            }
        }
        if (resolved == null) {
            resolved = buildKey(signLanguage, meaning, "");
        }
        boolean changed =
                !Objects.equals(resolved, key)
                        || !Objects.equals(lateral, sign.getLateralAnimationUrl());
        sign.setAnimationUrl(resolved);
        sign.setLateralAnimationUrl(lateral);
        return changed;
    }

    private static boolean isDefaultKey(String key, SignLanguage signLanguage, String meaning) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String prefix = signLanguage.getCode().toLowerCase(Locale.ROOT) + "/";
        if (!key.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            return false;
        }
        String stem = stripExtension(key.substring(prefix.length()));
        if (stem == null) {
            return false;
        }
        String name = normalize(stem);
        return name.equals(normalize(meaning)) || name.equals(normalize(fileName(meaning)));
    }

    /** The name the bucket file carries for this meaning: an alias, or the meaning itself. */
    private static String fileName(String meaning) {
        String key = normalize(meaning);
        return FILE_NAME_ALIASES.entrySet().stream()
                .filter(alias -> normalize(alias.getKey()).equals(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(meaning);
    }

    /**
     * The name without {@code .glb}/{@code .mp4}; the name itself if it has none; null otherwise.
     */
    private static String stripExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".glb") || lower.endsWith(".mp4")) {
            return name.substring(0, name.length() - 4);
        }
        return name.contains(".") ? null : name;
    }

    /** Lowercase, accent-free, with every run of non-alphanumerics collapsed into one space. */
    static String normalize(String text) {
        String plain =
                Normalizer.normalize(text, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT);
        return plain.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * What the bucket holds per language, keyed by normalized name + suffix, listed lazily and once
     * per import so a large catalog costs one listing rather than one HEAD per sign. An exact HEAD
     * is still tried first, which also covers storage that cannot be listed (the public bucket).
     */
    private final class MediaIndex {

        private final Map<String, Map<String, String>> byLanguage = new HashMap<>();

        String find(SignLanguage language, String meaning, String suffix) {
            String exact = buildKey(language, meaning, suffix);
            if (mediaStorage.exists(exact)) {
                return exact;
            }
            Map<String, String> keys = keys(language);
            String found = keys.get(normalize(meaning) + "|" + suffix);
            return found != null ? found : keys.get(normalize(fileName(meaning)) + "|" + suffix);
        }

        private Map<String, String> keys(SignLanguage language) {
            return byLanguage.computeIfAbsent(language.getCode(), code -> load(language));
        }

        private Map<String, String> load(SignLanguage language) {
            String prefix = language.getCode().toLowerCase(Locale.ROOT) + "/";
            Map<String, String> keys = new HashMap<>();
            for (String key : mediaStorage.listKeys(prefix)) {
                String name = key.substring(prefix.length());
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
                keys.putIfAbsent(normalize(stem) + "|" + suffix, key);
            }
            log.info("Indexed {} media files under '{}'", keys.size(), prefix);
            return keys;
        }
    }

    private Set<String> meanings(LoadedCourse course) {
        Set<String> meanings = new LinkedHashSet<>();
        for (TopicYaml topic : course.topics()) {
            for (LessonDto lesson : topic.lessons()) {
                lesson.blocks().forEach(block -> meanings.addAll(extractor.extract(block)));
            }
        }
        return meanings;
    }

    private static String buildKey(SignLanguage signLanguage, String meaning, String ext) {
        return signLanguage.getCode().toLowerCase(Locale.ROOT) + "/" + meaning + ext;
    }
}
