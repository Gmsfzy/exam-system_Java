package com.exam.backend.repository.bounty;

import com.exam.backend.domain.entity.bounty.BountySubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BountySubmissionRepository extends JpaRepository<BountySubmission, Long> {

    List<BountySubmission> findByBountyIdOrderByIdDesc(Long bountyId);

    List<BountySubmission> findByBountyIdAndStatusOrderByIdDesc(Long bountyId, String status);

    List<BountySubmission> findBySubmitterIdOrderByIdDesc(Long submitterId);

    Optional<BountySubmission> findByBountyIdAndSubmitterId(Long bountyId, Long submitterId);

    boolean existsByBountyIdAndSubmitterId(Long bountyId, Long submitterId);

    long countByBountyId(Long bountyId);

    long countByBountyIdAndStatus(Long bountyId, String status);

    /** 采纳一份后，其余 pending 投稿批量置 rejected（排除被采纳者） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE BountySubmission s SET s.status = 'rejected', s.reviewComment = :comment, s.reviewedAt = :now "
            + "WHERE s.bountyId = :bountyId AND s.status = 'pending' AND s.id <> :acceptedId")
    int rejectRemaining(@Param("bountyId") Long bountyId, @Param("acceptedId") Long acceptedId,
                        @Param("comment") String comment, @Param("now") LocalDateTime now);
}
