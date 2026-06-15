package com.signasource.signa_api.learning.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;

@Repository
public interface CourseVersionRepository extends JpaRepository<CourseVersion, UUID> {

	// Busca la versión activa (PUBLISHED) de un curso en particular
	Optional<CourseVersion> findByCourseIdAndStatus(UUID courseId, VersionStatus status);
}
