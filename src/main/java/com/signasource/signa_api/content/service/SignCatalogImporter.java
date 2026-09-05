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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SignCatalogImporter {

    private static final String ANIMATION_EXTENSION = ".glb";
    private static final Handedness DEFAULT_HANDEDNESS = Handedness.ONE_HANDED;

    private final SignCatalogExtractor extractor;
    private final SignRepository signRepository;
    private final SignLanguageRepository signLanguageRepository;

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
                if (!seen.add(meaning) || signRepository.existsByMeaning(meaning)) {
                    continue;
                }
                newSigns.add(
                        Sign.builder()
                                .meaning(meaning)
                                .handedness(DEFAULT_HANDEDNESS)
                                .animationUrl(objectKey(signLanguage, meaning))
                                .signLanguage(signLanguage)
                                .build());
            }
        }

        signRepository.saveAll(newSigns);
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

    private String objectKey(SignLanguage signLanguage, String meaning) {
        return signLanguage.getCode().toLowerCase(Locale.ROOT)
                + "/"
                + meaning
                + ANIMATION_EXTENSION;
    }
}
