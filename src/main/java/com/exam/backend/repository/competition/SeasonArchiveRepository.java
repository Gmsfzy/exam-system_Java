package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.SeasonArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeasonArchiveRepository extends JpaRepository<SeasonArchive, Long> {

    List<SeasonArchive> findByUserIdOrderBySeasonDesc(Long userId);

    List<SeasonArchive> findBySeasonOrderByRankAsc(String season);
}
