package com.signasource.signa_api.learning.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.dto.TopicSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

	private final CourseRepository courseRepository;
	private final CourseVersionRepository courseVersionRepository;

	@Transactional(readOnly = true)
	public Page<CourseSummaryResponse> getCoursesCatalog(UUID signLanguageId, Pageable pageable) {
		Page<Course> courses = courseRepository.findBySignLanguageId(signLanguageId, pageable);
		return courses.map(CourseSummaryResponse::from);
	}

	@Transactional(readOnly = true)
	public CourseDetailResponse getCourseDetail(UUID courseId) {

		CourseVersion activeVersion = courseVersionRepository.findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED)
				.orElseThrow(
						() -> new NotFoundException("Active published version not found for course ID: " + courseId));

		Course course = activeVersion.getCourse();
		List<Topic> topics = activeVersion.getTopics();

		List<TopicSummaryResponse> topicsResponse = topics.stream().map(TopicSummaryResponse::from).toList();

		return CourseDetailResponse.from(course, activeVersion, topicsResponse);
	}
}
