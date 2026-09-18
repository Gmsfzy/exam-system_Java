package com.exam.backend.repository.learning;

import com.exam.backend.domain.entity.learning.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    List<StudyPlan> findByUserIdOrderByIdDesc(Long userId);

    List<StudyPlan> findByUserIdAndStatus(Long userId, String status);

    /** 练习提交后自动推进进度：进行中的计划，专业/课程可选匹配（null 视为不限） */
    @Query("""
            SELECT p FROM StudyPlan p
            WHERE p.userId = :uid AND p.status = 'active'
              AND (:majorId IS NULL OR p.majorId IS NULL OR p.majorId = :majorId)
              AND (:courseId IS NULL OR p.courseId IS NULL OR p.courseId = :courseId)
            """)
    List<StudyPlan> findActiveMatching(@Param("uid") Long uid,
                                       @Param("majorId") Long majorId,
                                       @Param("courseId") Long courseId);
}
