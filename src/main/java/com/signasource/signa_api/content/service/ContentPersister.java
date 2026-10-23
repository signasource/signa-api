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
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a loaded course into the database, reusing the rows that are already there.
 *
 * <p>Rows are reconciled in place instead of being deleted and rebuilt. A rebuild looks simpler but
 * cannot work once anybody has used the app: progress, attempts, enrolments and learned signs all
 * point at these rows, so the delete hits a foreign key, the exception escapes {@code
 * ContentImportRunner} and the application fails to start. Editing content would take production
 * down.
 *
 * <p>Reconciling needs a stable identity per row. Courses, topics and lessons have a {@code code}
 * in the YAML. Blocks have none, so a block is identified by its type plus its serialized config:
 * the same block moved within its lesson keeps its attempts, while changing what it asks makes it a
 * new block. Duplicates of that key inside one lesson are matched in order of appearance.
 *
 * <p>Only content that disappears from the YAML is deleted, together with the user rows that hang
 * off it — those would be dangling references to an exercise that no longer exists.
 */
@Component
public class ContentPersister {

    private static final String HASH_ALGORITHM = "SHA-256";

    private final SignLanguageRepository signLanguageRepository;
    private final CourseRepository courseRepository;
    private final ContentPurger purger;
    private final ObjectMapper objectMapper;

    public ContentPersister(
            SignLanguageRepository signLanguageRepository,
            CourseRepository courseRepository,
            ContentPurger purger,
            ObjectMapper objectMapper) {
        this.signLanguageRepository = signLanguageRepository;
        this.courseRepository = courseRepository;
        this.purger = purger;
        this.objectMapper = objectMapper;
    }

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
        if (existing.isEmpty()) {
            courseRepository.save(buildCourse(loaded, signLanguage, contentHash));
            return ImportResult.CREATED;
        }

        Course course = existing.get();
        if (contentHash.equals(course.getContentHash())) {
            return ImportResult.UNCHANGED;
        }

        reconcileCourse(course, loaded, signLanguage, contentHash);
        courseRepository.save(course);
        return ImportResult.UPDATED;
    }

    // ── Reconciliation ──────────────────────────────────────────────────────────────────────────

    private void reconcileCourse(
            Course course, LoadedCourse loaded, SignLanguage signLanguage, String contentHash) {
        Course incoming = loaded.course().course().toEntity(signLanguage);
        course.setName(incoming.getName());
        course.setDescription(incoming.getDescription());
        course.setFree(incoming.isFree());
        course.setCoverUrl(incoming.getCoverUrl());
        course.setSignLanguage(signLanguage);
        course.setContentHash(contentHash);

        reconcileTopics(resolveVersion(course, loaded), loaded.topics());
    }

    /**
     * The version row to write the topics into.
     *
     * <p>Reused rather than replaced: enrolments and learned signs point at it, and the roadmap
     * expects a course to have exactly one published version, so adding a second would break every
     * read with a non-unique result.
     */
    private CourseVersion resolveVersion(Course course, LoadedCourse loaded) {
        CourseVersion incoming = loaded.course().version().toEntity(course);

        CourseVersion target =
                course.getVersions().stream()
                        .filter(v -> v.getVersion().equals(incoming.getVersion()))
                        .findFirst()
                        .or(
                                () ->
                                        course.getVersions().stream()
                                                .filter(
                                                        v ->
                                                                v.getStatus()
                                                                        == VersionStatus.PUBLISHED)
                                                .findFirst())
                        .orElse(null);

        if (target == null) {
            course.getVersions().add(incoming);
            return incoming;
        }

        target.setVersion(incoming.getVersion());
        target.setStatus(incoming.getStatus());
        if (incoming.getStatus() == VersionStatus.PUBLISHED && target.getPublishedAt() == null) {
            target.setPublishedAt(Instant.now());
        }
        return target;
    }

    private void reconcileTopics(CourseVersion version, List<TopicYaml> topicYamls) {
        Map<String, Topic> current = byKey(version.getTopics(), Topic::getCode);
        List<Topic> kept = new ArrayList<>();

        for (int i = 0; i < topicYamls.size(); i++) {
            TopicYaml yaml = topicYamls.get(i);
            Topic incoming = yaml.topic().toEntity(i, version);
            Topic topic = current.remove(yaml.topic().code());

            if (topic == null) {
                topic = incoming;
                version.getTopics().add(topic);
            } else {
                topic.setTitle(incoming.getTitle());
                topic.setSubtitle(incoming.getSubtitle());
                topic.setDescription(incoming.getDescription());
                topic.setCoverUrl(incoming.getCoverUrl());
                topic.setOrder(i);
            }

            reconcileLessons(topic, yaml.lessons());
            kept.add(topic);
        }

        for (Topic removed : current.values()) {
            purger.purgeTopic(removed);
        }
        version.getTopics().retainAll(kept);
    }

    private void reconcileLessons(Topic topic, List<LessonDto> lessonDtos) {
        Map<String, Lesson> current = byKey(topic.getLessons(), Lesson::getCode);
        List<Lesson> kept = new ArrayList<>();

        for (int i = 0; i < lessonDtos.size(); i++) {
            LessonDto dto = lessonDtos.get(i);
            Lesson incoming = dto.toEntity(i, topic);
            Lesson lesson = current.remove(dto.code());

            if (lesson == null) {
                lesson = incoming;
                topic.getLessons().add(lesson);
            } else {
                lesson.setName(incoming.getName());
                lesson.setDescription(incoming.getDescription());
                lesson.setOrder(i);
            }

            reconcileBlocks(lesson, dto.blocks());
            kept.add(lesson);
        }

        for (Lesson removed : current.values()) {
            purger.purgeLesson(removed);
        }
        topic.getLessons().retainAll(kept);
    }

    private void reconcileBlocks(Lesson lesson, List<LessonBlockDto> blockDtos) {
        Map<String, List<LessonBlock>> current = new LinkedHashMap<>();
        for (LessonBlock block : lesson.getLessonBlocks()) {
            current.computeIfAbsent(identityOf(block), k -> new ArrayList<>()).add(block);
        }

        List<LessonBlock> kept = new ArrayList<>();
        for (int i = 0; i < blockDtos.size(); i++) {
            LessonBlock incoming = blockDtos.get(i).toEntity(i, lesson, objectMapper);
            List<LessonBlock> candidates = current.get(identityOf(incoming));
            LessonBlock block =
                    candidates == null || candidates.isEmpty() ? null : candidates.remove(0);

            if (block == null) {
                block = incoming;
                lesson.getLessonBlocks().add(block);
            } else {
                block.setXpReward(incoming.getXpReward());
                block.setOrder(i);
            }
            kept.add(block);
        }

        for (List<LessonBlock> leftovers : current.values()) {
            leftovers.forEach(purger::purgeBlock);
        }
        lesson.getLessonBlocks().retainAll(kept);
    }

    /** A block is what it asks: its type plus its config. Moving it does not change who it is. */
    private String identityOf(LessonBlock block) {
        return block.getType() + "|" + block.getConfig();
    }

    private static <T> Map<String, T> byKey(
            List<T> items, java.util.function.Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(key.apply(item), item);
        }
        return map;
    }

    // ── First import ────────────────────────────────────────────────────────────────────────────

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

    private String fingerprint(LoadedCourse loaded) {
        try {
            byte[] canonical =
                    objectMapper.writeValueAsBytes(
                            new Object[] {
                                loaded.signLanguageCode(), loaded.course(), loaded.topics()
                            });
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ContentLoadException(
                    "Failed to fingerprint content for " + loaded.course().course().code(), e);
        }
    }
}
