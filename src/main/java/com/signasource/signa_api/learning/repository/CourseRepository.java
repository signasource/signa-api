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

    // Devuelve los cursos de un lenguaje específico, de forma paginada.
    Page<Course> findBySignLanguageId(UUID signLanguageId, Pageable pageable);

    // Cuando pedimos el detalle de un curso, queremos traer sus versiones en
    // la misma consulta SQL para saber cuál es la activa. @EntityGraph hace un LEFT JOIN.
    @EntityGraph(attributePaths = {"versions"})
    Optional<Course> findById(UUID id);
}
