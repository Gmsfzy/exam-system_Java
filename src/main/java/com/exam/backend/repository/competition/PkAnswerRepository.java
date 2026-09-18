package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.PkAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PkAnswerRepository extends JpaRepository<PkAnswer, Long> {

    List<PkAnswer> findByBattleIdAndPlayerId(Long battleId, Long playerId);

    boolean existsByBattleIdAndPlayerIdAndCqId(Long battleId, Long playerId, Long cqId);

    @Query("SELECT COALESCE(SUM(a.gainedScore), 0) FROM PkAnswer a "
            + "WHERE a.battleId = :bid AND a.playerId = :pid")
    double sumGainedScore(@Param("bid") Long battleId, @Param("pid") Long playerId);

    @Query("SELECT COUNT(a) FROM PkAnswer a WHERE a.battleId = :bid AND a.playerId = :pid")
    long countAnswered(@Param("bid") Long battleId, @Param("pid") Long playerId);

    @Modifying
    @Query("DELETE FROM PkAnswer a WHERE a.battleId = :bid")
    void deleteByBattleId(@Param("bid") Long battleId);
}
