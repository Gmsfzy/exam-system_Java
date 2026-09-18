package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.CompetitionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionAnswerRepository extends JpaRepository<CompetitionAnswer, Long> {

    List<CompetitionAnswer> findByParticipantId(Long participantId);

    Optional<CompetitionAnswer> findByParticipantIdAndCqId(Long participantId, Long cqId);

    boolean existsByParticipantIdAndCqId(Long participantId, Long cqId);

    /** 得分聚合：避免会话缓存重复计分，直接 SQL SUM 写回 participant.score */
    @Query("SELECT COALESCE(SUM(a.gainedScore), 0) FROM CompetitionAnswer a WHERE a.participantId = :pid")
    double sumGainedScore(@Param("pid") Long participantId);

    @Query("SELECT COALESCE(SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END), 0) FROM CompetitionAnswer a WHERE a.participantId = :pid")
    long countCorrect(@Param("pid") Long participantId);

    @Modifying
    @Query("DELETE FROM CompetitionAnswer a WHERE a.participantId = :pid")
    void deleteByParticipantId(@Param("pid") Long participantId);
}
