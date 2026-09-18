package com.exam.dao;

import com.exam.database.DatabaseManager;
import com.exam.enums.DifficultyEnum;
import com.exam.enums.QuestionTypeEnum;
import com.exam.models.Question;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuestionDao {

    public List<Question> findAll(Integer majorId, QuestionTypeEnum type, DifficultyEnum difficulty) {
        List<Question> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT q.*, m.name AS major_name FROM question q " +
                "LEFT JOIN major m ON q.major_id = m.id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (majorId != null) {
            sql.append(" AND q.major_id = ?");
            params.add(majorId);
        }
        if (type != null) {
            sql.append(" AND q.type = ?");
            params.add(type.getValue());
        }
        if (difficulty != null) {
            sql.append(" AND q.difficulty = ?");
            params.add(difficulty.getValue());
        }
        sql.append(" ORDER BY q.id DESC");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Optional<Question> findById(int id) {
        String sql = "SELECT q.*, m.name AS major_name FROM question q " +
                "LEFT JOIN major m ON q.major_id = m.id WHERE q.id = ?";
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

    public List<Question> findByIds(List<Integer> ids) {
        List<Question> list = new ArrayList<>();
        if (ids == null || ids.isEmpty()) return list;
        StringBuilder sb = new StringBuilder("SELECT q.*, m.name AS major_name FROM question q " +
                "LEFT JOIN major m ON q.major_id = m.id WHERE q.id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        sb.append(")");
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public int create(Question q) {
        String sql = "INSERT INTO question (content, options, answer, analysis, major_id, type, difficulty) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, q.getContent());
            ps.setString(2, DatabaseManager.toJsonList(q.getOptions()));
            ps.setString(3, q.getAnswer());
            ps.setString(4, q.getAnalysis());
            ps.setObject(5, q.getMajorId() > 0 ? q.getMajorId() : null);
            ps.setString(6, q.getType().getValue());
            ps.setString(7, q.getDifficulty().getValue());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean update(Question q) {
        String sql = "UPDATE question SET content=?, options=?, answer=?, analysis=?, major_id=?, type=?, difficulty=? WHERE id=?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q.getContent());
            ps.setString(2, DatabaseManager.toJsonList(q.getOptions()));
            ps.setString(3, q.getAnswer());
            ps.setString(4, q.getAnalysis());
            ps.setObject(5, q.getMajorId() > 0 ? q.getMajorId() : null);
            ps.setString(6, q.getType().getValue());
            ps.setString(7, q.getDifficulty().getValue());
            ps.setInt(8, q.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean delete(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM question WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private Question mapRow(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setId(rs.getInt("id"));
        q.setContent(rs.getString("content"));
        q.setOptions(DatabaseManager.fromJsonList(rs.getString("options")));
        q.setAnswer(rs.getString("answer"));
        q.setAnalysis(rs.getString("analysis"));
        q.setMajorId(rs.getInt("major_id"));
        q.setType(QuestionTypeEnum.fromValue(rs.getString("type")));
        q.setDifficulty(DifficultyEnum.fromValue(rs.getString("difficulty")));
        q.setCreatedAt(DatabaseManager.fromDbDateTime(rs.getString("created_at")));
        q.setMajorName(rs.getString("major_name"));
        return q;
    }
}