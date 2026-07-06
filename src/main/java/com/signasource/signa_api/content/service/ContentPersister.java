package com.signasource.signa_api.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.result.ImportResult;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.dto.yaml.LessonDto;
import com.signasource.signa_api.content.dto.yaml.TopicYaml;
import com.signasource.signa_api.content.exception.ContentLoadException;
import com.signasource.signa_api.content.exception.SignLanguageNotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContentPersister {

    private final SignLanguageRepository signLanguageRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    public ContentPersister(
            SignLanguageRepository signLanguageRepository,
            CourseRepository courseRepository,
            ObjectMapper objectMapper) {
        this.signLanguageRepository = signLanguageRepository;
        this.courseRepository = courseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Imports a course idempotently. If the course does not exist it is inserted; if it exists and
     * its content is unchanged (same fingerprint) nothing is written; if it exists and its content
     * changed, the previous course is deleted and reinserted from scratch. There is no versioning
     * and no attempt to preserve row identity: on change, the whole tree is replaced. Runs in a
     * single transaction, so a failure leaves the database untouched.
     */
    @Transactional
    public ImportResult importCourse(LoadedCourse loaded) {
        String courseCode = loaded.course().course().code();

        SignLanguage signLanguage =
                signLanguageRepository
                        .findByCode(loaded.signLanguageCode())
                        .orElseThrow(
                                () -> new SignLanguageNotFoundException(loaded.signLanguageCode()));

        String contentHash = fingerprint(loaded);

        Optional<Course> existing = courseRepository.findByCode(courseCode);
        if (existing.isPresent()) {
            if (contentHash.equals(existing.get().getContentHash())) {
                return ImportResult.UNCHANGED;
            }
            courseRepository.delete(existing.get());
            courseRepository.flush();
        }

        courseRepository.save(buildCourse(loaded, signLanguage, contentHash));
        return existing.isPresent() ? ImportResult.UPDATED : ImportResult.CREATED;
    }

    private Course buildCourse(LoadedCourse loaded, SignLanguage signLanguage, String contentHash) {
        Course course = loaded.course().course().toEntity(signLanguage);
        course.setContentHash(contentHash);

        CourseVersion courseVersion = loaded.course().version().toEntity(course);
        course.getVersions().add(courseVersion);

        List<TopicYaml> topicYamls = loaded.topics();
        for (int i = 0; i < topicYamls.size(); i++) {
            TopicYaml topicYaml = topicYamls.get(i);
            Topic topic = topicYaml.topic().toEntity(i, courseVersion);
            courseVersion.getTopics().add(topic);

            List<LessonDto> lessonDtos = topicYaml.lessons();
            for (int j = 0; j < lessonDtos.size(); j++) {
                LessonDto lessonDto = lessonDtos.get(j);
                Lesson lesson = lessonDto.toEntity(j, topic);
                topic.getLessons().add(lesson);

                List<LessonBlockDto> blocks = lessonDto.blocks();
                for (int k = 0; k < blocks.size(); k++) {
                    lesson.getLessonBlocks().add(blocks.get(k).toEntity(k, lesson, objectMapper));
                }
            }
        }
        return course;
    }

    /**
     * SHA-256 hex fingerprint of the loaded content. Derived from the parsed DTO tree (not the raw
     * bytes), so cosmetic edits (whitespace, comments, key order preserved by the YAML parser) that
     * do not change the meaning produce the same hash and skip the re-import.
     */
    private String fingerprint(LoadedCourse loaded) {
        try {
            byte[] canonical =
                    objectMapper.writeValueAsBytes(
                            new Object[] {
                                loaded.signLanguageCode(), loaded.course(), loaded.topics()
                            });
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ContentLoadException(
                    "Failed to fingerprint content for " + loaded.course().course().code(), e);
        }
    }
}
