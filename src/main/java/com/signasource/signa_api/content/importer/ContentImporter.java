package com.signasource.signa_api.content.importer;

import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.dto.LessonDto;
import com.signasource.signa_api.content.dto.TopicYaml;
import com.signasource.signa_api.content.exception.CourseAlreadyExistsException;
import com.signasource.signa_api.content.exception.SignLanguageNotFoundException;
import com.signasource.signa_api.content.loader.LoadedCourse;
import com.signasource.signa_api.content.mapper.CourseMapper;
import com.signasource.signa_api.content.mapper.CourseVersionMapper;
import com.signasource.signa_api.content.mapper.LessonBlockMapper;
import com.signasource.signa_api.content.mapper.LessonMapper;
import com.signasource.signa_api.content.mapper.TopicMapper;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContentImporter {

    private final SignLanguageRepository signLanguageRepository;
    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final CourseVersionMapper courseVersionMapper;
    private final TopicMapper topicMapper;
    private final LessonMapper lessonMapper;
    private final LessonBlockMapper lessonBlockMapper;

    public ContentImporter(
            SignLanguageRepository signLanguageRepository,
            CourseRepository courseRepository,
            CourseMapper courseMapper,
            CourseVersionMapper courseVersionMapper,
            TopicMapper topicMapper,
            LessonMapper lessonMapper,
            LessonBlockMapper lessonBlockMapper) {
        this.signLanguageRepository = signLanguageRepository;
        this.courseRepository = courseRepository;
        this.courseMapper = courseMapper;
        this.courseVersionMapper = courseVersionMapper;
        this.topicMapper = topicMapper;
        this.lessonMapper = lessonMapper;
        this.lessonBlockMapper = lessonBlockMapper;
    }

    @Transactional
    public void importCourse(LoadedCourse loaded) {
        String courseCode = loaded.course().course().code();

        SignLanguage signLanguage =
                signLanguageRepository
                        .findByCode(loaded.signLanguageCode())
                        .orElseThrow(
                                () -> new SignLanguageNotFoundException(loaded.signLanguageCode()));

        if (courseRepository.existsByCode(courseCode)) {
            throw new CourseAlreadyExistsException(courseCode);
        }

        Course course = courseMapper.toEntity(loaded.course().course(), signLanguage);
        CourseVersion courseVersion = courseVersionMapper.toEntity(loaded.course().version(), course);
        course.getVersions().add(courseVersion);

        List<TopicYaml> topicYamls = loaded.topics();
        for (int i = 0; i < topicYamls.size(); i++) {
            TopicYaml topicYaml = topicYamls.get(i);
            Topic topic = topicMapper.toEntity(topicYaml.topic(), i, courseVersion);
            courseVersion.getTopics().add(topic);

            List<LessonDto> lessonDtos = topicYaml.lessons();
            for (int j = 0; j < lessonDtos.size(); j++) {
                LessonDto lessonDto = lessonDtos.get(j);
                Lesson lesson = lessonMapper.toEntity(lessonDto, j, topic);
                topic.getLessons().add(lesson);

                List<LessonBlockDto> blocks = lessonDto.blocks();
                for (int k = 0; k < blocks.size(); k++) {
                    lesson.getLessonBlocks().add(lessonBlockMapper.toEntity(blocks.get(k), k, lesson));
                }
            }
        }

        courseRepository.save(course);
    }
}
