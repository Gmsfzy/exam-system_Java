package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.UserPointsProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPointsProfileRepository extends JpaRepository<UserPointsProfile, Long> {

    Optional<UserPointsProfile> findByUserIdAndSeason(Long userId, String season);

    List<UserPointsProfile> findBySeasonOrderByPointsDescIdAsc(String season);

    List<UserPointsProfile> findBySeason(String season);
}
