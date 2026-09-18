package com.exam.backend.repository;

import com.exam.backend.domain.entity.Answer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    Optional<Answer> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    List<Answer> findBySessionId(Long sessionId);

    List<Answer> findBySessionIdIn(List<Long> sessionIds);

    List<Answer> findBySessionIdAndGradeStatus(Long sessionId, String gradeStatus);

    /**
     * CAS 抢占待判分主观题：pending_ai，以及超时卡死的 grading（重排）→ 置为 grading。
     * failed 为终态（已转人工），不自动重试。批量 UPDATE 不触发 JPA 审计，
     * 故显式刷新 updated_at，供补偿扫描判定“判分中”是否卡死。
     *
     * @return 实际抢占到的行数（并发下仅一方能拿到某行的抢占权）
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Answer a SET a.gradeStatus = 'grading', a.updatedAt = :now "
            + "WHERE a.sessionId = :sid AND ("
            + "  a.gradeStatus = 'pending_ai' "
            + "  OR (a.gradeStatus = 'grading' AND a.updatedAt < :staleBefore))")
    int claimForGrading(@Param("sid") Long sessionId,
                        @Param("staleBefore") LocalDateTime staleBefore,
                        @Param("now") LocalDateTime now);

    /** 会话内仍待 AI 判分的主观题数（pending_ai / grading，不含 failed 终态） */
    @Query("SELECT COUNT(a) FROM Answer a WHERE a.sessionId = :sid "
            + "AND a.gradeStatus IN ('pending_ai','grading')")
    long countUngraded(@Param("sid") Long sessionId);

    /** 补偿扫描：存在待判分或卡死“判分中”题目的会话（去重、限量） */
    @Query("SELECT DISTINCT a.sessionId FROM Answer a WHERE a.gradeStatus = 'pending_ai' "
            + "OR (a.gradeStatus = 'grading' AND a.updatedAt < :staleBefore)")
    List<Long> findSessionsNeedingGrading(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);
}
