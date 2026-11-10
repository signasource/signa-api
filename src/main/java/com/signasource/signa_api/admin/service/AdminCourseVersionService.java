package com.signasource.signa_api.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.admin.dto.AdminCourseVersionDetailResponse;
import com.signasource.signa_api.admin.dto.AdminCourseVersionListItemResponse;
import com.signasource.signa_api.admin.dto.CreateCourseRequest;
import com.signasource.signa_api.admin.dto.SaveCourseVersionRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCourseVersionService {

    private static final String LSA_CODE = "LSA";

    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final SignLanguageRepository signLanguageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AdminCourseVersionListItemResponse> listVersions() {
        return courseVersionRepository.findAllByOrderByStatusAscUpdatedAtDesc().stream()
                .map(AdminCourseVersionListItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCourseVersionDetailResponse getVersion(UUID id) {
        CourseVersion version =
                courseVersionRepository
                        .findWithFullTreeById(id)
                        .orElseThrow(() -> new NotFoundException("Course version not found"));
        return AdminCourseVersionDetailResponse.from(version, objectMapper);
    }

    @Transactional
    public AdminCourseVersionDetailResponse createCourse(
            CreateCourseRequest request, String adminEmail) {
        SignLanguage lsa =
                signLanguageRepository
                        .findByCode(LSA_CODE)
                        .orElseThrow(() -> new NotFoundException("Sign language LSA not found"));

        Course course =
                Course.builder()
                        .code(
                                "ui-"
                                        + UUID.randomUUID()
                                                .toString()
                                                .replace("-", "")
                                                .substring(0, 12))
                        .name(request.name())
                        .isFree(true)
                        .signLanguage(lsa)
                        .build();
        courseRepository.save(course);

        CourseVersion version =
                CourseVersion.builder()
                        .version("1")
                        .status(VersionStatus.DRAFT)
                        .course(course)
                        .updatedAt(Instant.now())
                        .updatedBy(adminEmail)
                        .build();
        courseVersionRepository.save(version);

        return AdminCourseVersionDetailResponse.from(version, objectMapper);
    }

    @Transactional
    public AdminCourseVersionDetailResponse forkVersion(UUID sourceId, String adminEmail) {
        CourseVersion source =
                courseVersionRepository
                        .findWithFullTreeById(sourceId)
                        .orElseThrow(() -> new NotFoundException("Course version not found"));

        int nextVersion = nextVersionNumber(source.getCourse().getId());

        CourseVersion draft =
                CourseVersion.builder()
                        .version(String.valueOf(nextVersion))
                        .status(VersionStatus.DRAFT)
                        .course(source.getCourse())
                        .basedOnVersion(source.getVersion())
                        .updatedAt(Instant.now())
                        .updatedBy(adminEmail)
                        .build();

        List<Topic> copiedTopics = new ArrayList<>();
        for (Topic srcTopic : source.getTopics()) {
            Topic topic =
                    Topic.builder()
                            .code("ui-" + shortId())
                            .title(srcTopic.getTitle())
                            .subtitle(srcTopic.getSubtitle())
                            .description(srcTopic.getDescription())
                            .order(srcTopic.getOrder())
                            .courseVersion(draft)
                            .build();
            List<Lesson> copiedLessons = new ArrayList<>();
            for (Lesson srcLesson : srcTopic.getLessons()) {
                Lesson lesson =
                        Lesson.builder()
                                .code("ui-" + shortId())
                                .name(srcLesson.getName())
                                .description(srcLesson.getDescription())
                                .order(srcLesson.getOrder())
                                .topic(topic)
                                .build();
                List<LessonBlock> copiedBlocks = new ArrayList<>();
                for (LessonBlock srcBlock : srcLesson.getLessonBlocks()) {
                    copiedBlocks.add(
                            LessonBlock.builder()
                                    .type(srcBlock.getType())
                                    .order(srcBlock.getOrder())
                                    .config(srcBlock.getConfig())
                                    .xpReward(srcBlock.getXpReward())
                                    .lesson(lesson)
                                    .build());
                }
                lesson.getLessonBlocks().addAll(copiedBlocks);
                copiedLessons.add(lesson);
            }
            topic.getLessons().addAll(copiedLessons);
            copiedTopics.add(topic);
        }
        draft.getTopics().addAll(copiedTopics);
        courseVersionRepository.save(draft);

        return AdminCourseVersionDetailResponse.from(draft, objectMapper);
    }

    @Transactional
    public AdminCourseVersionDetailResponse saveVersion(
            UUID id, SaveCourseVersionRequest req, String adminEmail) {
        CourseVersion version =
                courseVersionRepository
                        .findWithFullTreeById(id)
                        .orElseThrow(() -> new NotFoundException("Course version not found"));
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new InvalidInputException("Only draft versions can be edited");
        }

        Course course = version.getCourse();
        course.setName(req.name());
        course.setDescription(req.description());
        course.setFree(req.isFree());
        course.setPrice(req.price());
        courseRepository.save(course);

        version.getTopics().clear();

        List<Topic> newTopics = new ArrayList<>();
        List<SaveCourseVersionRequest.TopicData> topicDataList =
                req.topics() != null ? req.topics() : List.of();
        for (int ti = 0; ti < topicDataList.size(); ti++) {
            SaveCourseVersionRequest.TopicData td = topicDataList.get(ti);
            Topic topic =
                    Topic.builder()
                            .code("ui-" + shortId())
                            .title(td.title())
                            .subtitle(td.subtitle())
                            .description(td.description())
                            .order(ti)
                            .courseVersion(version)
                            .build();
            List<Lesson> newLessons = new ArrayList<>();
            List<SaveCourseVersionRequest.LessonData> lessonDataList =
                    td.lessons() != null ? td.lessons() : List.of();
            for (int li = 0; li < lessonDataList.size(); li++) {
                SaveCourseVersionRequest.LessonData ld = lessonDataList.get(li);
                Lesson lesson =
                        Lesson.builder()
                                .code("ui-" + shortId())
                                .name(ld.name())
                                .description(ld.description())
                                .order(li)
                                .topic(topic)
                                .build();
                List<LessonBlock> newBlocks = new ArrayList<>();
                List<SaveCourseVersionRequest.BlockData> blockDataList =
                        ld.blocks() != null ? ld.blocks() : List.of();
                for (int bi = 0; bi < blockDataList.size(); bi++) {
                    SaveCourseVersionRequest.BlockData bd = blockDataList.get(bi);
                    newBlocks.add(
                            LessonBlock.builder()
                                    .type(BlockType.valueOf(bd.type()))
                                    .order(bi)
                                    .config(serializeConfig(bd.config()))
                                    .xpReward(bd.xpReward())
                                    .lesson(lesson)
                                    .build());
                }
                lesson.getLessonBlocks().addAll(newBlocks);
                newLessons.add(lesson);
            }
            topic.getLessons().addAll(newLessons);
            newTopics.add(topic);
        }
        version.getTopics().addAll(newTopics);
        version.setUpdatedAt(Instant.now());
        version.setUpdatedBy(adminEmail);
        courseVersionRepository.save(version);

        return AdminCourseVersionDetailResponse.from(version, objectMapper);
    }

    @Transactional
    public AdminCourseVersionDetailResponse publishVersion(UUID id) {
        CourseVersion version =
                courseVersionRepository
                        .findWithFullTreeById(id)
                        .orElseThrow(() -> new NotFoundException("Course version not found"));
        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new InvalidInputException("Only draft versions can be published");
        }

        courseVersionRepository.findByCourseId(version.getCourse().getId()).stream()
                .filter(v -> v.getStatus() == VersionStatus.PUBLISHED)
                .forEach(
                        v -> {
                            v.setStatus(VersionStatus.ARCHIVED);
                            courseVersionRepository.save(v);
                        });

        version.setStatus(VersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        courseVersionRepository.save(version);

        return AdminCourseVersionDetailResponse.from(version, objectMapper);
    }

    private int nextVersionNumber(UUID courseId) {
        return courseVersionRepository.findByCourseId(courseId).stream()
                        .mapToInt(
                                v -> {
                                    try {
                                        return Integer.parseInt(v.getVersion());
                                    } catch (NumberFormatException e) {
                                        return 0;
                                    }
                                })
                        .max()
                        .orElse(0)
                + 1;
    }

    private String serializeConfig(java.util.Map<String, Object> config) {
        if (config == null || config.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
