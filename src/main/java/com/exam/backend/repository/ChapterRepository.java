package com.exam.backend.repository;

import com.exam.backend.domain.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByCourseId(Long courseId);
    List<Chapter> findByCourseIdOrderByOrderNumAsc(Long courseId);
}
