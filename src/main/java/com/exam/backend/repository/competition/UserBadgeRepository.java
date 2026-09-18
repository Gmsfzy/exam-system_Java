package com.exam.backend.repository.competition;

import com.exam.backend.domain.entity.competition.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserIdOrderByGrantedAtDesc(Long userId);

    Optional<UserBadge> findByUserIdAndBadgeCodeAndSeason(Long userId, String badgeCode, String season);

    boolean existsByUserIdAndBadgeCode(Long userId, String badgeCode);
}
