package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.CompetitionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionParticipantRepository extends JpaRepository<CompetitionParticipant, Long> {

    Optional<CompetitionParticipant> findByCompetitionIdAndUserId(Long competitionId, Long userId);

    List<CompetitionParticipant> findByCompetitionId(Long competitionId);

    List<CompetitionParticipant> findByUserId(Long userId);

    List<CompetitionParticipant> findByCompetitionIdAndStatusIn(Long competitionId, List<String> statuses);

    /** 榜单：已开局（playing/finished）参与者，得分↓用时↑开始时间↑ */
    @Query("SELECT p FROM CompetitionParticipant p WHERE p.competitionId = :cid "
            + "AND p.status <> 'joined' "
            + "ORDER BY p.score DESC, p.usedTime ASC, p.startedAt ASC")
    List<CompetitionParticipant> findLeaderboard(@Param("cid") Long cid);

    long countByCompetitionIdAndStatus(Long competitionId, String status);

    /** 定时扫描：某状态下仍有参与者的竞赛 id（用于批量结算） */
    @Modifying
    @Query("DELETE FROM CompetitionParticipant p WHERE p.competitionId = :cid")
    void deleteByCompetitionId(@Param("cid") Long cid);
}
