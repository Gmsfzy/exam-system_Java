package com.exam.backend.repository.bounty;

import com.exam.backend.domain.entity.bounty.Bounty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BountyRepository extends JpaRepository<Bounty, Long> {

    List<Bounty> findAllByOrderByIdDesc();

    List<Bounty> findByPublisherIdOrderByIdDesc(Long publisherId);

    /**
     * CAS 抢采纳锁：仅当仍为 open 时置 closed 并写入 acceptedSubmissionId/closedAt。
     * rowcount=1 者胜（并发只采纳一次）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Bounty b SET b.status = 'closed', b.acceptedSubmissionId = :sid, b.closedAt = :now "
            + "WHERE b.id = :id AND b.status = 'open'")
    int claimClose(@Param("id") Long id, @Param("sid") Long sid, @Param("now") LocalDateTime now);
}
