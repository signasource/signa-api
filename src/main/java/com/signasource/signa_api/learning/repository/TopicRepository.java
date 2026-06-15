package com.signasource.signa_api.learning.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.signasource.signa_api.learning.entity.Topic;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    // Trae todos los módulos de una versión de curso, ORDENADOS por la columna "order"
    List<Topic> findByCourseVersionIdOrderByOrderAsc(UUID courseVersionId);
}
