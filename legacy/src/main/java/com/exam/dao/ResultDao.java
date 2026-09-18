package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.models.Result;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ResultDao {

    public List<Result> findAll() {
        List<Result> list = new ArrayList<>();
        String sql = "SELECT r.*, e.title AS exam_title, u.username AS s_name FROM result r " +
                "INNER JOIN exam e ON r.exam_id = e.id " +
                "INNER JOIN user u ON r.student_id = u.id ORDER BY r.submitted_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Result> findByStudent(int studentId) {
        List<Result> list = new ArrayList<>();
        String sql = "SELECT r.*, e.title AS exam_title FROM result r " +
                "INNER JOIN exam e ON r.exam_id = e.id WHERE r.student_id = ? ORDER BY r.submitted_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Result> findByExam(int examId) {
        List<Result> list = new ArrayList<>();
        String sql = "SELECT r.*, e.title AS exam_title, u.username AS s_name FROM result r " +
                "INNER JOIN exam e ON r.exam_id = e.id " +
                "INNER JOIN user u ON r.student_id = u.id WHERE r.exam_id = ? ORDER BY r.score DESC";
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

    public Optional<Result> findById(int id) {
        String sql = "SELECT r.*, e.title AS exam_title, u.username AS s_name FROM result r " +
                "INNER JOIN exam e ON r.exam_id = e.id " +
                "INNER JOIN user u ON r.student_id = u.id WHERE r.id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public Optional<Result> findByExamAndStudent(int examId, int studentId) {
        String sql = "SELECT r.*, e.title AS exam_title, u.username AS s_name FROM result r " +
                "INNER JOIN exam e ON r.exam_id = e.id " +
                "INNER JOIN user u ON r.student_id = u.id WHERE r.exam_id = ? AND r.student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public int create(Result result) {
        String sql = "INSERT INTO result (exam_id, student_id, score, total_score, ai_analysis) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, result.getExamId());
            ps.setInt(2, result.getStudentId());
            ps.setDouble(3, result.getScore());
            ps.setDouble(4, result.getTotalScore());
            ps.setString(5, result.getAiAnalysis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean updateScore(int id, double score) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE result SET score = ? WHERE id = ?")) {
            ps.setDouble(1, score);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public String findStudentName(int studentId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT username FROM user WHERE id = ?")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateAIAnalysis(int id, String analysis) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE result SET ai_analysis = ? WHERE id = ?")) {
            ps.setString(1, analysis);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private Result mapRow(ResultSet rs) throws SQLException {
        Result r = new Result();
        r.setId(rs.getInt("id"));
        r.setExamId(rs.getInt("exam_id"));
        r.setStudentId(rs.getInt("student_id"));
        r.setScore(rs.getDouble("score"));
        r.setTotalScore(rs.getDouble("total_score"));
        r.setSubmittedAt(DatabaseManager.fromDbDateTime(rs.getString("submitted_at")));
        r.setAiAnalysis(rs.getString("ai_analysis"));
        r.setExamTitle(rs.getString("exam_title"));
        try {
            r.setStudentName(rs.getString("s_name"));
        } catch (SQLException ignored) {
        }
        return r;
    }
}