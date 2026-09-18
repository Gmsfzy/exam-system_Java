package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.enums.ExamStatusEnum;
import com.exam.models.Exam;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExamDao {

    public List<Exam> findByStudentInvited(int studentId) {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT e.*, u.username AS creator_name, " +
                "(SELECT COUNT(*) FROM exam_question eq WHERE eq.exam_id = e.id) AS q_count, " +
                "(SELECT COUNT(*) FROM exam_student es WHERE es.exam_id = e.id) AS s_count " +
                "FROM exam e LEFT JOIN user u ON e.creator_id = u.id " +
                "INNER JOIN exam_student es ON es.exam_id = e.id " +
                "WHERE es.student_id = ? ORDER BY e.id DESC";
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

    public List<Exam> findByCreator(int creatorId) {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT e.*, u.username AS creator_name, " +
                "(SELECT COUNT(*) FROM exam_question eq WHERE eq.exam_id = e.id) AS q_count, " +
                "(SELECT COUNT(*) FROM exam_student es WHERE es.exam_id = e.id) AS s_count " +
                "FROM exam e LEFT JOIN user u ON e.creator_id = u.id " +
                "WHERE e.creator_id = ? ORDER BY e.id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, creatorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Exam> findByStudentInvited(int studentId, ExamStatusEnum status) {
        List<Exam> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT e.*, u.username AS creator_name, " +
                "(SELECT COUNT(*) FROM exam_question eq WHERE eq.exam_id = e.id) AS q_count, " +
                "(SELECT COUNT(*) FROM exam_student es WHERE es.exam_id = e.id) AS s_count " +
                "FROM exam e LEFT JOIN user u ON e.creator_id = u.id " +
                "INNER JOIN exam_student es ON es.exam_id = e.id " +
                "WHERE es.student_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(studentId);
        if (status != null) {
            sql.append(" AND e.status = ?");
            params.add(status.getValue());
        }
        sql.append(" ORDER BY e.start_time DESC");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Exam> findAll() {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT e.*, u.username AS creator_name, " +
                "(SELECT COUNT(*) FROM exam_question eq WHERE eq.exam_id = e.id) AS q_count, " +
                "(SELECT COUNT(*) FROM exam_student es WHERE es.exam_id = e.id) AS s_count " +
                "FROM exam e LEFT JOIN user u ON e.creator_id = u.id ORDER BY e.id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean delete(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM exam WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<Exam> findById(int id) {
        String sql = "SELECT e.*, u.username AS creator_name, " +
                "(SELECT COUNT(*) FROM exam_question eq WHERE eq.exam_id = e.id) AS q_count, " +
                "(SELECT COUNT(*) FROM exam_student es WHERE es.exam_id = e.id) AS s_count " +
                "FROM exam e LEFT JOIN user u ON e.creator_id = u.id WHERE e.id = ?";
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

    public int create(Exam exam) {
        String sql = "INSERT INTO exam (title, description, start_time, end_time, duration, status, creator_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, exam.getTitle());
            ps.setString(2, exam.getDescription());
            ps.setString(3, DatabaseManager.toDbDateTime(exam.getStartTime()));
            ps.setString(4, DatabaseManager.toDbDateTime(exam.getEndTime()));
            ps.setInt(5, exam.getDuration());
            ps.setString(6, exam.getStatus().getValue());
            ps.setInt(7, exam.getCreatorId());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean update(Exam exam) {
        String sql = "UPDATE exam SET title=?, description=?, start_time=?, end_time=?, duration=?, status=? WHERE id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exam.getTitle());
            ps.setString(2, exam.getDescription());
            ps.setString(3, DatabaseManager.toDbDateTime(exam.getStartTime()));
            ps.setString(4, DatabaseManager.toDbDateTime(exam.getEndTime()));
            ps.setInt(5, exam.getDuration());
            ps.setString(6, exam.getStatus().getValue());
            ps.setInt(7, exam.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean updateStatus(int id, ExamStatusEnum status) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE exam SET status = ? WHERE id = ?")) {
            ps.setString(1, status.getValue());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<Exam> findExpiredPublished() {
        List<Exam> list = new ArrayList<>();
        String sql = "SELECT e.*, u.username AS creator_name, " +
                "(SELECT COUNT(*) FROM exam_question eq WHERE eq.exam_id = e.id) AS q_count, " +
                "(SELECT COUNT(*) FROM exam_student es WHERE es.exam_id = e.id) AS s_count " +
                "FROM exam e LEFT JOIN user u ON e.creator_id = u.id " +
                "WHERE e.status = 'published' AND e.end_time < CURRENT_TIMESTAMP";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Exam mapRow(ResultSet rs) throws SQLException {
        Exam e = new Exam();
        e.setId(rs.getInt("id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setStartTime(DatabaseManager.fromDbDateTime(rs.getString("start_time")));
        e.setEndTime(DatabaseManager.fromDbDateTime(rs.getString("end_time")));
        e.setDuration(rs.getInt("duration"));
        e.setStatus(ExamStatusEnum.fromValue(rs.getString("status")));
        e.setCreatorId(rs.getInt("creator_id"));
        e.setCreatedAt(DatabaseManager.fromDbDateTime(rs.getString("created_at")));
        e.setCreatorName(rs.getString("creator_name"));
        e.setQuestionCount(rs.getInt("q_count"));
        e.setStudentCount(rs.getInt("s_count"));
        return e;
    }
}