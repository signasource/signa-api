package com.signasource.signa_api.learning.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.signasource.signa_api.learning.entity.Course;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

	Page<Course> findBySignLanguageId(UUID signLanguageId, Pageable pageable);

	@EntityGraph(attributePaths = {"versions"})
	Optional<Course> findById(UUID id);
}
