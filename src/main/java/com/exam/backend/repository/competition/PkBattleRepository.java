package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.PkBattle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PkBattleRepository extends JpaRepository<PkBattle, Long> {

    List<PkBattle> findByStatusOrderByIdDesc(String status);

    List<PkBattle> findByChallengerIdOrderByIdDesc(Long challengerId);

    List<PkBattle> findByOpponentIdOrderByIdDesc(Long opponentId);

    /** 同一竞赛同时只能有一个等待中的挑战 */
    boolean existsByCompetitionIdAndChallengerIdAndStatus(Long competitionId, Long challengerId, String status);

    /** waiting 超时候选（lobby/state 入口惰性清理） */
    List<PkBattle> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

    /**
     * CAS 抢结算权：仅当 status='playing' 时置 finished，rowcount=1 者胜（并发只结算一次）。
     * 返回受影响行数。
     */
    @Modifying
    @Query("UPDATE PkBattle b SET b.status = 'finished' WHERE b.id = :id AND b.status = 'playing'")
    int claimSettlement(@Param("id") Long id);

    /** CAS 取消过期等待：仅当仍为 waiting 时置 cancelled */
    @Modifying
    @Query("UPDATE PkBattle b SET b.status = 'cancelled' WHERE b.id = :id AND b.status = 'waiting'")
    int claimCancel(@Param("id") Long id);
}
