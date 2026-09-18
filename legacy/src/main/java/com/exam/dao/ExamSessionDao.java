package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.enums.SessionStatusEnum;
import com.exam.models.ExamSession;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExamSessionDao {

    public Optional<ExamSession> findActive(int examId, int studentId) {
        String sql = "SELECT * FROM exam_session WHERE exam_id = ? AND student_id = ? AND status = 'in_progress' ORDER BY id DESC LIMIT 1";
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

    public Optional<ExamSession> findById(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM exam_session WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public int create(int examId, int studentId) {
        String sql = "INSERT INTO exam_session (exam_id, student_id, start_time, status, switch_count) VALUES (?, ?, ?, 'in_progress', 0)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            ps.setString(3, DatabaseManager.toDbDateTime(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean updateStatus(int id, SessionStatusEnum status) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_session SET status = ?, end_time = ? WHERE id = ?")) {
            ps.setString(1, status.getValue());
            ps.setString(2, DatabaseManager.toDbDateTime(LocalDateTime.now()));
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean incrementSwitchCount(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_session SET switch_count = switch_count + 1 WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<ExamSession> findActiveSessionsByExam(int examId) {
        List<ExamSession> list = new ArrayList<>();
        String sql = "SELECT * FROM exam_session WHERE exam_id = ? AND status = 'in_progress'";
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

    private ExamSession mapRow(ResultSet rs) throws SQLException {
        ExamSession s = new ExamSession();
        s.setId(rs.getInt("id"));
        s.setExamId(rs.getInt("exam_id"));
        s.setStudentId(rs.getInt("student_id"));
        s.setStartTime(DatabaseManager.fromDbDateTime(rs.getString("start_time")));
        s.setEndTime(DatabaseManager.fromDbDateTime(rs.getString("end_time")));
        s.setStatus(SessionStatusEnum.fromValue(rs.getString("status")));
        s.setSwitchCount(rs.getInt("switch_count"));
        return s;
    }
}