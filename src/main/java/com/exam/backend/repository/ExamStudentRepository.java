package com.exam.backend.repository;

import com.exam.backend.domain.entity.ExamStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamStudentRepository extends JpaRepository<ExamStudent, ExamStudent.PK> {

    List<ExamStudent> findByExamId(Long examId);
    List<ExamStudent> findByStudentId(Long studentId);
    long countByExamId(Long examId);
    void deleteByExamIdAndStudentId(Long examId, Long studentId);
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
}
