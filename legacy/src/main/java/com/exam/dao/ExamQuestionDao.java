package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.models.ExamQuestion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamQuestionDao {

    public List<ExamQuestion> findByExamId(int examId) {
        List<ExamQuestion> list = new ArrayList<>();
        String sql = "SELECT eq.*, q.content AS q_content, q.type AS q_type " +
                "FROM exam_question eq INNER JOIN question q ON eq.question_id = q.id " +
                "WHERE eq.exam_id = ? ORDER BY eq.order_num";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Integer> findQuestionIds(int examId) {
        List<Integer> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT question_id FROM exam_question WHERE exam_id = ? ORDER BY order_num")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getInt("question_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean addQuestion(int examId, int questionId, double score, int order) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO exam_question (exam_id, question_id, score, order_num) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, examId);
            ps.setInt(2, questionId);
            ps.setDouble(3, score);
            ps.setInt(4, order);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean removeQuestion(int examId, int questionId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM exam_question WHERE exam_id = ? AND question_id = ?")) {
            ps.setInt(1, examId);
            ps.setInt(2, questionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public void reorder(int examId) {
        List<ExamQuestion> qs = findByExamId(examId);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_question SET order_num = ? WHERE exam_id = ? AND question_id = ?")) {
            for (int i = 0; i < qs.size(); i++) {
                ps.setInt(1, i + 1);
                ps.setInt(2, examId);
                ps.setInt(3, qs.get(i).getQuestionId());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public double getTotalScore(int examId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(SUM(score), 0) AS total FROM exam_question WHERE exam_id = ?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int countByExam(int examId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM exam_question WHERE exam_id = ?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("cnt");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private ExamQuestion mapRow(ResultSet rs) throws SQLException {
        ExamQuestion eq = new ExamQuestion();
        eq.setExamId(rs.getInt("exam_id"));
        eq.setQuestionId(rs.getInt("question_id"));
        eq.setScore(rs.getDouble("score"));
        eq.setOrder(rs.getInt("order_num"));
        eq.setQuestionContent(rs.getString("q_content"));
        eq.setQuestionType(rs.getString("q_type"));
        return eq;
    }
}