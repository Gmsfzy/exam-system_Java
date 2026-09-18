package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.Competition;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    List<Competition> findByCreatorIdOrderByIdDesc(Long creatorId);

    List<Competition> findByStatusInOrderByIdDesc(List<String> statuses);

    /** 到点转 ongoing / 过点转 ended 的惰性同步候选 */
    List<Competition> findByStatusInAndStartTimeBeforeAndEndTimeAfter(
            List<String> fromStatuses, LocalDateTime now, LocalDateTime after);

    List<Competition> findByStatusAndEndTimeBefore(String status, LocalDateTime now);

    /** 限时赛积分统一结算 CAS：仅当 points_settled=false 时置真并返回受影响行数 */
    @Modifying
    @Query("UPDATE Competition c SET c.pointsSettled = true " +
            "WHERE c.id = :id AND c.pointsSettled = false")
    int claimPointsSettlement(@Param("id") Long id);

    Optional<Competition> findByIdAndCreatorId(Long id, Long creatorId);
}
