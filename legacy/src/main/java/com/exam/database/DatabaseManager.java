package com.exam.database;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DatabaseManager {
    private static final String DB_PATH = "exam_system.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            initializeDatabase();
        } catch (Exception e) {
            System.err.println("数据库初始化警告: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static String toDbDateTime(LocalDateTime dt) {
        if (dt == null) return null;
        return dt.format(DT_FMT);
    }

    public static LocalDateTime fromDbDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJsonList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<String> fromJsonList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) return result;
        try {
            String content = json.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }
            int i = 0;
            while (i < content.length()) {
                char c = content.charAt(i);
                if (c == '"') {
                    int j = i + 1;
                    StringBuilder sb = new StringBuilder();
                    while (j < content.length() && content.charAt(j) != '"') {
                        if (content.charAt(j) == '\\' && j + 1 < content.length()) {
                            sb.append(content.charAt(j + 1));
                            j += 2;
                        } else {
                            sb.append(content.charAt(j));
                            j++;
                        }
                    }
                    result.add(sb.toString());
                    i = j + 1;
                } else if (c == ',') {
                    i++;
                } else {
                    i++;
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void tryAddColumn(Connection conn, String table, String columnDef) {
        try (Statement s = conn.createStatement()) {
            String colName = columnDef.trim().split("\\s+")[0];
            ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")");
            boolean exists = false;
            while (rs.next()) {
                if (colName.equalsIgnoreCase(rs.getString("name"))) { exists = true; break; }
            }
            rs.close();
            if (!exists) {
                s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnDef);
                System.out.println("[DB] 新增列 " + table + "." + colName);
            }
        } catch (SQLException e) {
            System.out.println("[DB] 新增列失败: " + e.getMessage());
        }
    }

    private static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=30000;");
            stmt.execute("PRAGMA foreign_keys=ON;");

            stmt.execute("CREATE TABLE IF NOT EXISTS user (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "email VARCHAR(100), " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS major (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "description TEXT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS question (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "content TEXT NOT NULL, " +
                    "options TEXT, " +
                    "answer TEXT NOT NULL, " +
                    "analysis TEXT, " +
                    "major_id INTEGER, " +
                    "type VARCHAR(50) NOT NULL, " +
                    "difficulty VARCHAR(20) NOT NULL, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS exam (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title VARCHAR(200) NOT NULL, " +
                    "description TEXT, " +
                    "start_time DATETIME, " +
                    "end_time DATETIME, " +
                    "duration INTEGER NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'draft', " +
                    "creator_id INTEGER NOT NULL, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS exam_question (" +
                    "exam_id INTEGER NOT NULL, " +
                    "question_id INTEGER NOT NULL, " +
                    "score REAL NOT NULL DEFAULT 10, " +
                    "order_num INTEGER NOT NULL, " +
                    "PRIMARY KEY (exam_id, question_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS exam_student (" +
                    "exam_id INTEGER NOT NULL, " +
                    "student_id INTEGER NOT NULL, " +
                    "invited_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (exam_id, student_id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS exam_session (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "exam_id INTEGER NOT NULL, " +
                    "student_id INTEGER NOT NULL, " +
                    "start_time DATETIME, " +
                    "end_time DATETIME, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'in_progress', " +
                    "switch_count INTEGER NOT NULL DEFAULT 0)");

            stmt.execute("CREATE TABLE IF NOT EXISTS answer (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "session_id INTEGER NOT NULL, " +
                    "question_id INTEGER NOT NULL, " +
                    "student_answer TEXT, " +
                    "is_correct BOOLEAN, " +
                    "score REAL, " +
                    "manual_score REAL, " +
                    "manual_comment TEXT, " +
                    "needs_manual_grade BOOLEAN DEFAULT 0, " +
                    "ai_score REAL, " +
                    "ai_analysis TEXT)");

            stmt.execute("CREATE TABLE IF NOT EXISTS result (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "exam_id INTEGER NOT NULL, " +
                    "student_id INTEGER NOT NULL, " +
                    "score REAL NOT NULL DEFAULT 0, " +
                    "total_score REAL NOT NULL DEFAULT 0, " +
                    "submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "ai_analysis TEXT)");

            // 迁移：为旧数据库补列（忽略已存在的列错误）
            tryAddColumn(conn, "answer", "ai_score REAL");
            tryAddColumn(conn, "answer", "ai_analysis TEXT");

            seedInitialData(conn);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void seedInitialData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM user")) {
            if (rs.next() && rs.getInt("cnt") == 0) {
                String teacherPwd = hashPassword("123123");
                String studentPwd = hashPassword("123123");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO user (username, password_hash, role) VALUES (?, ?, ?)")) {
                    ps.setString(1, "nfpz");
                    ps.setString(2, teacherPwd);
                    ps.setString(3, "teacher");
                    ps.executeUpdate();

                    ps.setString(1, "123");
                    ps.setString(2, studentPwd);
                    ps.setString(3, "student");
                    ps.executeUpdate();

                    ps.setString(1, "student2");
                    ps.setString(2, studentPwd);
                    ps.setString(3, "student");
                    ps.executeUpdate();
                }
            }
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM major")) {
            if (rs.next() && rs.getInt("cnt") == 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO major (name, description) VALUES (?, ?)")) {
                    ps.setString(1, "计算机科学");
                    ps.setString(2, "计算机基础、编程、算法等相关题目");
                    ps.executeUpdate();
                    ps.setString(1, "数学");
                    ps.setString(2, "高等数学、线性代数、概率统计等");
                    ps.executeUpdate();
                }
            }
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM question")) {
            if (rs.next() && rs.getInt("cnt") == 0) {
                seedSampleQuestions(conn);
            }
        }
    }

    private static void seedSampleQuestions(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO question (content, options, answer, analysis, major_id, type, difficulty) VALUES (?, ?, ?, ?, ?, ?, ?)")) {

            ps.setString(1, "以下哪种语言是计算机可以直接执行的？");
            ps.setString(2, toJsonList(Arrays.asList("结构化语言", "机器语言", "高级语言", "汇编语言")));
            ps.setString(3, "B");
            ps.setString(4, "机器语言由0和1组成，是计算机可以直接识别和执行的语言。");
            ps.setInt(5, 1);
            ps.setString(6, "single_choice");
            ps.setString(7, "easy");
            ps.executeUpdate();

            ps.setString(1, "以下哪些是面向对象的特性？");
            ps.setString(2, toJsonList(Arrays.asList("封装", "编译", "继承", "多态")));
            ps.setString(3, "A,C,D");
            ps.setString(4, "封装、继承、多态是面向对象的三大特性。");
            ps.setInt(5, 1);
            ps.setString(6, "multiple_choice");
            ps.setString(7, "medium");
            ps.executeUpdate();

            ps.setString(1, "1 + 1 = ____");
            ps.setString(2, toJsonList(new ArrayList<String>()));
            ps.setString(3, "2");
            ps.setString(4, "基础算术，1加1等于2。");
            ps.setInt(5, 2);
            ps.setString(6, "fill_blank");
            ps.setString(7, "easy");
            ps.executeUpdate();

            ps.setString(1, "Java是一种面向对象的编程语言。");
            ps.setString(2, toJsonList(new ArrayList<String>()));
            ps.setString(3, "正确");
            ps.setString(4, "Java支持类、继承、多态等面向对象特性。");
            ps.setInt(5, 1);
            ps.setString(6, "true_false");
            ps.setString(7, "easy");
            ps.executeUpdate();

            ps.setString(1, "请简述面向对象的三大特性。");
            ps.setString(2, toJsonList(new ArrayList<String>()));
            ps.setString(3, "封装、继承、多态");
            ps.setString(4, "封装：隐藏内部实现；继承：子类继承父类；多态：同一接口不同实现。");
            ps.setInt(5, 1);
            ps.setString(6, "short_answer");
            ps.setString(7, "medium");
            ps.executeUpdate();
        }
    }

    // 简化的密码哈希（在没有bcrypt库时作为备选）
    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] salt = "exam_system_salt".getBytes();
            md.update(salt);
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return "sha256:" + sb.toString();
        } catch (Exception e) {
            return "plain:" + password;
        }
    }

    public static boolean checkPassword(String password, String hash) {
        if (hash == null) return false;
        if (hash.startsWith("sha256:")) {
            return hashPassword(password).equals(hash);
        }
        return hash.equals(password);
    }
}