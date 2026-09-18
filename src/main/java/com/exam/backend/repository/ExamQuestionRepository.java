package com.exam.backend.repository;

import com.exam.backend.domain.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestion.PK> {

    List<ExamQuestion> findByExamId(Long examId);

    List<ExamQuestion> findByExamIdOrderByOrderAsc(Long examId);

    long countByExamId(Long examId);

    void deleteByExamIdAndQuestionId(Long examId, Long questionId);

    @Query("SELECT eq.questionId FROM ExamQuestion eq WHERE eq.examId = :examId")
    List<Long> findQuestionIdsByExamId(@Param("examId") Long examId);

    boolean existsByQuestionId(Long questionId);
}
