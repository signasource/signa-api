package com.signasource.signa_api.learning.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.signasource.signa_api.learning.entity.LessonBlock;

@Repository
public interface LessonBlockRepository extends JpaRepository<LessonBlock, UUID> {

    // Trae los bloques de contenido para que la app arme la interfaz de la lección
    List<LessonBlock> findByLessonIdOrderByOrderAsc(UUID lessonId);
}
