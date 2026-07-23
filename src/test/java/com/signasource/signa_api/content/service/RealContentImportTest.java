package com.signasource.signa_api.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.validator.ContentValidator;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class RealContentImportTest {

    private static final String SIGN_LANG_CODE = "LSA";
    private static final String COURSE_CODE = "basic-course";

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private ContentLoader contentLoader;
    @Autowired private ContentValidator contentValidator;
    @Autowired private ContentPersister contentPersister;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;

    private LoadedCourse basicCourse;

    @BeforeEach
    void setUp() {
        basicCourse = contentLoader.load(SIGN_LANG_CODE, COURSE_CODE);
    }

    @AfterEach
    void tearDown() {
        lessonBlockRepository.deleteAll();
        lessonRepository.deleteAll();
        topicRepository.deleteAll();
        courseVersionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void shouldValidateRealContent() {
        assertThatNoException().isThrownBy(() -> contentValidator.validate(basicCourse));
    }

    @Test
    void shouldImportRealContent() {
        int expectedTopics = basicCourse.topics().size();
        int expectedLessons = basicCourse.topics().stream()
                .mapToInt(t -> t.lessons().size())
                .sum();
        int expectedBlocks = basicCourse.topics().stream()
                .flatMap(t -> t.lessons().stream())
                .mapToInt(l -> l.blocks().size())
                .sum();

        contentPersister.importCourse(basicCourse);

        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(topicRepository.count()).isEqualTo(expectedTopics);
        assertThat(lessonRepository.count()).isEqualTo(expectedLessons);
        assertThat(lessonBlockRepository.count()).isEqualTo(expectedBlocks);

        Course course = courseRepository.findAll().get(0);
        assertThat(course.getCode()).isEqualTo(COURSE_CODE);
    }

    @Test
    void shouldImportSinglePublishedVersion() {
        contentPersister.importCourse(basicCourse);

        assertThat(courseVersionRepository.count()).isEqualTo(1);
        CourseVersion version = courseVersionRepository.findAll().get(0);
        assertThat(version.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(version.getPublishedAt()).isNotNull();
    }
}
