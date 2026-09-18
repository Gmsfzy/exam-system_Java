package com.exam.backend.repository;

import com.exam.backend.domain.entity.Question;
import com.exam.backend.domain.enums.DifficultyEnum;
import com.exam.backend.domain.enums.QuestionTypeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByMajorIdAndTypeAndDifficulty(Long majorId, QuestionTypeEnum type, DifficultyEnum difficulty);

    List<Question> findByIsPublicTrue();

    List<Question> findByCreatorId(Long creatorId);

    List<Question> findByIdIn(List<Long> ids);

    /**
     * 按范围查询题目:
     * scope=all    → 所有题目（公开+本人创建）
     * scope=mine   → 仅本人创建
     * scope=public → 仅公开题库
     * majorId/courseId/chapterId/type/difficulty 为可选过滤条件（null 表示不过滤）
     */
    @Query("""
            SELECT q FROM Question q
            WHERE (
                    (:scope = 'mine' AND q.creatorId = :uid)
                 OR (:scope = 'public' AND q.isPublic = true)
                 OR (:scope = 'all' AND (q.isPublic = true OR q.creatorId = :uid))
                )
              AND (:majorId IS NULL OR q.majorId = :majorId)
              AND (:courseId IS NULL OR q.courseId = :courseId)
              AND (:chapterId IS NULL OR q.chapterId = :chapterId)
              AND (:type IS NULL OR q.type = :type)
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
            ORDER BY q.createdAt DESC
            """)
    Page<Question> findScoped(@Param("scope") String scope,
                              @Param("uid") Long uid,
                              @Param("majorId") Long majorId,
                              @Param("courseId") Long courseId,
                              @Param("chapterId") Long chapterId,
                              @Param("type") QuestionTypeEnum type,
                              @Param("difficulty") DifficultyEnum difficulty,
                              Pageable pageable);

    @Query("""
            SELECT q FROM Question q
            WHERE (:majorId IS NULL OR q.majorId = :majorId)
              AND (:courseId IS NULL OR q.courseId = :courseId)
              AND (:chapterId IS NULL OR q.chapterId = :chapterId)
              AND (:type IS NULL OR q.type = :type)
              AND (:difficulty IS NULL OR q.difficulty = :difficulty)
              AND (q.isPublic = true OR q.creatorId = :uid)
            """)
    List<Question> filterForUser(@Param("majorId") Long majorId,
                                 @Param("courseId") Long courseId,
                                 @Param("chapterId") Long chapterId,
                                 @Param("type") QuestionTypeEnum type,
                                 @Param("difficulty") DifficultyEnum difficulty,
                                 @Param("uid") Long uid);

    /**
     * 自主学习抽题 (v4.0)：按专业/课程/章节可选过滤，只抽指定题型（客观题），
     * 可见范围同 filterForUser（公开题 + 本人创建题）。
     */
    @Query("""
            SELECT q FROM Question q
            WHERE (:majorId IS NULL OR q.majorId = :majorId)
              AND (:courseId IS NULL OR q.courseId = :courseId)
              AND (:chapterId IS NULL OR q.chapterId = :chapterId)
              AND q.type IN :types
              AND (q.isPublic = true OR q.creatorId = :uid)
            """)
    List<Question> findPracticePool(@Param("majorId") Long majorId,
                                    @Param("courseId") Long courseId,
                                    @Param("chapterId") Long chapterId,
                                    @Param("types") List<QuestionTypeEnum> types,
                                    @Param("uid") Long uid);
}
