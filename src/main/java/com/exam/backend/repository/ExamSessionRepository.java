package com.exam.backend.repository;

import com.exam.backend.domain.entity.ExamSession;
import com.exam.backend.domain.enums.SessionStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    Optional<ExamSession> findByExamIdAndStudentIdAndStatus(Long examId, Long studentId, SessionStatusEnum status);

    /**
     * 取指定状态下最新一条会话（兼容历史数据中同一状态存在多条记录的情况，
     * 避免 Optional 单条查询抛 IncorrectResultSizeDataAccessException）
     */
    Optional<ExamSession> findFirstByExamIdAndStudentIdAndStatusOrderByIdDesc(
            Long examId, Long studentId, SessionStatusEnum status);

    List<ExamSession> findByExamIdAndStatus(Long examId, SessionStatusEnum status);

    List<ExamSession> findByStudentId(Long studentId);

    List<ExamSession> findByStatusAndExamIdIn(SessionStatusEnum status, List<Long> examIds);
}
