package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.models.ExamStudent;
import com.exam.models.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExamStudentDao {

    public List<ExamStudent> findByExamId(int examId) {
        List<ExamStudent> list = new ArrayList<>();
        String sql = "SELECT es.*, u.username AS s_name FROM exam_student es " +
                "INNER JOIN user u ON es.student_id = u.id WHERE es.exam_id = ? ORDER BY es.invited_at";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExamStudent es = new ExamStudent();
                    es.setExamId(rs.getInt("exam_id"));
                    es.setStudentId(rs.getInt("student_id"));
                    es.setInvitedAt(DatabaseManager.fromDbDateTime(rs.getString("invited_at")));
                    es.setStudentName(rs.getString("s_name"));
                    list.add(es);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean invite(int examId, int studentId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO exam_student (exam_id, student_id) VALUES (?, ?)")) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean remove(int examId, int studentId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM exam_student WHERE exam_id = ? AND student_id = ?")) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isInvited(int examId, int studentId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM exam_student WHERE exam_id = ? AND student_id = ?")) {
            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public int countByExam(int examId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) AS cnt FROM exam_student WHERE exam_id = ?")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("cnt");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}