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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SignCatalogImporter {

    private static final Handedness DEFAULT_HANDEDNESS = Handedness.ONE_HANDED;

    private final SignCatalogExtractor extractor;
    private final SignRepository signRepository;
    private final SignLanguageRepository signLanguageRepository;
    private final SignMediaStorage mediaStorage;

    @Transactional
    public void importSigns(List<LoadedCourse> courses) {
        List<Sign> newSigns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

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
                                    boolean mediaDrift = syncMedia(existing, signLanguage, meaning);
                                    if (meaningDrift || mediaDrift) {
                                        existing.setMeaning(meaning);
                                        signRepository.save(existing);
                                    }
                                },
                                () -> newSigns.add(newSign(signLanguage, meaning)));
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
        for (Sign sign : signRepository.findAll()) {
            SignLanguage language = sign.getSignLanguage();
            if (language != null && syncMedia(sign, language, sign.getMeaning())) {
                signRepository.save(sign);
                changed++;
            }
        }
        return changed;
    }

    private Sign newSign(SignLanguage signLanguage, String meaning) {
        Sign sign =
                Sign.builder()
                        .meaning(meaning)
                        .handedness(DEFAULT_HANDEDNESS)
                        .signLanguage(signLanguage)
                        .build();
        syncMedia(sign, signLanguage, meaning);
        return sign;
    }

    /**
     * Points the sign at what is really in the bucket: {@code <meaning>.glb}, else {@code
     * <meaning>.mp4} (plus {@code _lateral.mp4} if present), else the bare {@code <meaning>} key
     * with no extension. A key that is not one of those defaults was uploaded from the panel and is
     * left alone. Returns whether anything changed.
     */
    private boolean syncMedia(Sign sign, SignLanguage signLanguage, String meaning) {
        String key = sign.getAnimationUrl();
        if (!isDefaultKey(key, signLanguage, meaning)) {
            return false;
        }
        String glbKey = buildKey(signLanguage, meaning, ".glb");
        String mp4Key = buildKey(signLanguage, meaning, ".mp4");
        String resolved;
        String lateral = null;
        if (mediaStorage.exists(glbKey)) {
            resolved = glbKey;
        } else if (mediaStorage.exists(mp4Key)) {
            resolved = mp4Key;
            String lateralKey = buildKey(signLanguage, meaning, "_lateral.mp4");
            lateral = mediaStorage.exists(lateralKey) ? lateralKey : null;
        } else {
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
        return key == null
                || key.isBlank()
                || key.equalsIgnoreCase(buildKey(signLanguage, meaning, ".glb"))
                || key.equalsIgnoreCase(buildKey(signLanguage, meaning, ".mp4"))
                || key.equalsIgnoreCase(buildKey(signLanguage, meaning, ""));
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
