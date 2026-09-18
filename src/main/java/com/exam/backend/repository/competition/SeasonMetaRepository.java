package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.SeasonMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonMetaRepository extends JpaRepository<SeasonMeta, Long> {

    Optional<SeasonMeta> findBySeason(String season);

    List<SeasonMeta> findByArchivedTrueOrderBySeasonDesc();

    /** 归档 CAS 抢锁：仅当未归档时置 archived=true，返回受影响行数 */
    @Modifying
    @Query("UPDATE SeasonMeta s SET s.archived = true, s.archivedAt = :now "
            + "WHERE s.season = :season AND s.archived = false")
    int claimArchive(@Param("season") String season, @Param("now") java.time.LocalDateTime now);
}
