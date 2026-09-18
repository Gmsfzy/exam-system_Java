package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.models.Answer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnswerDao {

    public Optional<Answer> findBySessionAndQuestion(int sessionId, int questionId) {
        String sql = "SELECT a.* FROM answer a WHERE a.session_id = ? AND a.question_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public List<Answer> findBySessionId(int sessionId) {
        List<Answer> list = new ArrayList<>();
        String sql = "SELECT a.*, q.content AS q_content, q.answer AS q_answer, q.type AS q_type, eq.score AS q_score " +
                "FROM answer a " +
                "INNER JOIN question q ON a.question_id = q.id " +
                "LEFT JOIN exam_question eq ON eq.question_id = q.id AND eq.exam_id = " +
                "(SELECT exam_id FROM exam_session WHERE id = ?) " +
                "WHERE a.session_id = ? ORDER BY a.id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRowDetailed(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public int saveOrUpdate(Answer answer) {
        Optional<Answer> existing = findBySessionAndQuestion(answer.getSessionId(), answer.getQuestionId());
        if (existing.isPresent()) {
            String sql = "UPDATE answer SET student_answer = ?, is_correct = ?, score = ?, manual_score = ?, manual_comment = ?, needs_manual_grade = ?, ai_score = ?, ai_analysis = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, answer.getStudentAnswer());
                ps.setObject(2, answer.getIsCorrect());
                ps.setObject(3, answer.getScore());
                ps.setObject(4, answer.getManualScore());
                ps.setString(5, answer.getManualComment());
                ps.setBoolean(6, answer.isNeedsManualGrade());
                ps.setObject(7, answer.getAiScore());
                ps.setString(8, answer.getAiAnalysis());
                ps.setInt(9, existing.get().getId());
                ps.executeUpdate();
                return existing.get().getId();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            String sql = "INSERT INTO answer (session_id, question_id, student_answer, is_correct, score, manual_score, manual_comment, needs_manual_grade, ai_score, ai_analysis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, answer.getSessionId());
                ps.setInt(2, answer.getQuestionId());
                ps.setString(3, answer.getStudentAnswer());
                ps.setObject(4, answer.getIsCorrect());
                ps.setObject(5, answer.getScore());
                ps.setObject(6, answer.getManualScore());
                ps.setString(7, answer.getManualComment());
                ps.setBoolean(8, answer.isNeedsManualGrade());
                ps.setObject(9, answer.getAiScore());
                ps.setString(10, answer.getAiAnalysis());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public boolean updateManualGrade(int id, double manualScore, String comment) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE answer SET manual_score = ?, manual_comment = ?, needs_manual_grade = 0 WHERE id = ?")) {
            ps.setDouble(1, manualScore);
            ps.setString(2, comment);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<Answer> findById(int id) {
        String sql = "SELECT a.*, q.content AS q_content, q.answer AS q_answer, q.type AS q_type, " +
                "COALESCE(eq.score, 10) AS q_score " +
                "FROM answer a " +
                "INNER JOIN question q ON a.question_id = q.id " +
                "LEFT JOIN exam_question eq ON eq.question_id = q.id AND eq.exam_id = " +
                "(SELECT exam_id FROM exam_session WHERE id = a.session_id) " +
                "WHERE a.id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowDetailed(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public boolean hasSubjectivePending(int sessionId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM answer WHERE session_id = ? AND needs_manual_grade = 1 LIMIT 1")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean updateAI(int id, Double aiScore, String aiAnalysis) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE answer SET ai_score = ?, ai_analysis = ? WHERE id = ?")) {
            ps.setObject(1, aiScore);
            ps.setString(2, aiAnalysis);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Answer mapRow(ResultSet rs) throws SQLException {
        Answer a = new Answer();
        a.setId(rs.getInt("id"));
        a.setSessionId(rs.getInt("session_id"));
        a.setQuestionId(rs.getInt("question_id"));
        a.setStudentAnswer(rs.getString("student_answer"));
        a.setIsCorrect((Boolean) rs.getObject("is_correct"));
        a.setScore((Double) rs.getObject("score"));
        a.setManualScore((Double) rs.getObject("manual_score"));
        a.setManualComment(rs.getString("manual_comment"));
        a.setNeedsManualGrade(rs.getBoolean("needs_manual_grade"));
        try { a.setAiScore((Double) rs.getObject("ai_score")); } catch (SQLException ignored) {}
        try { a.setAiAnalysis(rs.getString("ai_analysis")); } catch (SQLException ignored) {}
        return a;
    }

    private Answer mapRowDetailed(ResultSet rs) throws SQLException {
        Answer a = mapRow(rs);
        a.setQuestionContent(rs.getString("q_content"));
        a.setCorrectAnswer(rs.getString("q_answer"));
        a.setQuestionType(rs.getString("q_type"));
        a.setQuestionScore(rs.getDouble("q_score"));
        return a;
    }
}