package com.exam.api;

import com.exam.dao.*;
import com.exam.models.*;
import com.exam.enums.*;
import com.exam.database.DatabaseManager;
import com.exam.ai.AIClient;
import com.exam.ai.AIService;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiServer {
    private static final int PORT = 8080;
    private static final Map<String, User> sessions = new ConcurrentHashMap<>();

    private final UserDao userDao = new UserDao();
    private final MajorDao majorDao = new MajorDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ExamDao examDao = new ExamDao();
    private final ExamQuestionDao examQuestionDao = new ExamQuestionDao();
    private final ExamStudentDao examStudentDao = new ExamStudentDao();
    private final ExamSessionDao examSessionDao = new ExamSessionDao();
    private final AnswerDao answerDao = new AnswerDao();
    private final ResultDao resultDao = new ResultDao();
    private final AIService aiService = new AIService(new AIClient());

    public static void main(String[] args) throws Exception {
        new ApiServer().start();
    }

    public void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/auth/login", this::handleLogin);
        server.createContext("/api/auth/register", this::handleRegister);
        server.createContext("/api/auth/logout", this::handleLogout);
        server.createContext("/api/auth/me", this::handleMe);
        server.createContext("/api/majors", this::handleMajors);
        server.createContext("/api/questions", this::handleQuestions);
        server.createContext("/api/exams", this::handleExams);
        server.createContext("/api/take/session", this::handleTakeSession);
        server.createContext("/api/take/answer", this::handleTakeAnswer);
        server.createContext("/api/take/submit", this::handleTakeSubmit);
        server.createContext("/api/take/switch", this::handleTakeSwitch);
        server.createContext("/api/results", this::handleResults);
        server.createContext("/api/grading/pending", this::handleGradingPending);
        server.createContext("/api/grading/answer", this::handleGradingAnswer);
        server.createContext("/api/ai/generate", this::handleAIGenerate);
        server.createContext("/api/ai/grade", this::handleAIGrade);
        server.createContext("/api/ai/analyze", this::handleAIAnalyze);
        server.createContext("/api/ai/recommend", this::handleAIRecommend);
        server.createContext("/api/users/students", this::handleStudents);
        server.createContext("/api/", this::handleRoot);
        server.createContext("/", this::handleRoot);
        server.setExecutor(null);
        server.start();
        System.out.println("Exam System API Server started on http://localhost:" + PORT);
    }

    private String getSessionId(HttpExchange ex) {
        return ex.getRequestHeaders().getFirst("X-Session-Id");
    }

    private User currentUser(HttpExchange ex) {
        String sid = getSessionId(ex);
        if (sid == null) return null;
        return sessions.get(sid);
    }

    private void write(HttpExchange ex, int status, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Session-Id");
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private void ok(HttpExchange ex, Object obj) throws IOException {
        write(ex, 200, Json.toJson(obj));
    }

    private void ok(HttpExchange ex, int status, Object obj) throws IOException {
        write(ex, status, Json.toJson(obj));
    }

    private void err(HttpExchange ex, int status, String msg) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        write(ex, status, Json.toJson(m));
    }

    private Map<String, Object> body(HttpExchange ex) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String s = sb.toString().trim();
            if (s.isEmpty()) return new LinkedHashMap<>();
            return Json.parse(s);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return java.net.URLDecoder.decode(kv[1], "UTF-8"); } catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }

    private boolean needLogin(HttpExchange ex) throws IOException {
        if (currentUser(ex) == null) { err(ex, 401, "Not logged in"); return false; }
        return true;
    }

    private boolean needTeacher(HttpExchange ex) throws IOException {
        User u = currentUser(ex);
        if (u == null) { err(ex, 401, "Not logged in"); return false; }
        if (!u.isTeacher()) { err(ex, 403, "Teacher required"); return false; }
        return true;
    }

    private boolean isOptions(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            write(ex, 204, "");
            return true;
        }
        return false;
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "exam-system");
        info.put("version", "1.0.0");
        info.put("apiBase", "/api");
        write(ex, 200, Json.toJson(info));
    }

    // ===== Auth =====
    private void handleLogin(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        String username = (String) b.get("username");
        String password = (String) b.get("password");
        if (username == null || password == null) { err(ex, 400, "Missing username or password"); return; }
        Optional<User> opt = userDao.findByUsername(username);
        if (!opt.isPresent()) { err(ex, 401, "Invalid credentials"); return; }
        User u = opt.get();
        String hash = u.getPasswordHash();
        boolean ok = DatabaseManager.checkPassword(password, hash);
        if (!ok) { err(ex, 401, "Invalid credentials"); return; }
        String sid = UUID.randomUUID().toString();
        sessions.put(sid, u);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", sid);
        resp.put("user", mapUser(u));
        ok(ex, resp);
    }

    private void handleRegister(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        String username = (String) b.get("username");
        String password = (String) b.get("password");
        String role = (String) b.get("role");
        if (username == null || password == null) { err(ex, 400, "Missing params"); return; }
        if (role == null) role = "student";
        Optional<User> exist = userDao.findByUsername(username);
        if (exist.isPresent()) { err(ex, 409, "Username exists"); return; }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(DatabaseManager.hashPassword(password));
        try { u.setRole(RoleEnum.fromValue(role)); } catch (Exception e) { err(ex, 400, "Invalid role"); return; }
        int id = userDao.create(u);
        u.setId(id);
        String sid = UUID.randomUUID().toString();
        sessions.put(sid, u);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", sid);
        resp.put("user", mapUser(u));
        ok(ex, 201, resp);
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        String sid = getSessionId(ex);
        if (sid != null) sessions.remove(sid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        ok(ex, m);
    }

    private void handleMe(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needLogin(ex)) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", mapUser(currentUser(ex)));
        ok(ex, m);
    }

    private Map<String, Object> mapUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole().getValue());
        m.put("roleLabel", u.getRole().getLabel());
        return m;
    }

    // ===== Majors =====
    private void handleMajors(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        if (method.equals("GET")) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Major m : majorDao.findAll()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", m.getId());
                item.put("name", m.getName());
                item.put("description", m.getDescription());
                item.put("questionCount", majorDao.countQuestions(m.getId()));
                list.add(item);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("data", list);
            ok(ex, r);
            return;
        }
        if (!needTeacher(ex)) return;
        if (method.equals("POST")) {
            Map<String, Object> b = body(ex);
            Major m = new Major();
            m.setName((String) b.get("name"));
            m.setDescription((String) b.get("description"));
            if (m.getName() == null || m.getName().isEmpty()) { err(ex, 400, "name required"); return; }
            int id = majorDao.create(m);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", id);
            r.put("name", m.getName());
            r.put("description", m.getDescription());
            ok(ex, 201, r);
            return;
        }
        if (method.equals("PUT") || method.equals("DELETE")) {
            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            int id;
            try { id = Integer.parseInt(parts[parts.length - 1]); } catch (Exception e) { err(ex, 400, "Invalid id"); return; }
            if (method.equals("PUT")) {
                Map<String, Object> b = body(ex);
                Major m = new Major();
                m.setId(id);
                m.setName((String) b.get("name"));
                m.setDescription((String) b.get("description"));
                majorDao.update(m);
            } else {
                majorDao.delete(id);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", true);
            ok(ex, r);
            return;
        }
        err(ex, 405, "Method not allowed");
    }

    // ===== Questions =====
    private void handleQuestions(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        if (method.equals("GET")) {
            String mid = query(ex, "majorId");
            String type = query(ex, "type");
            String diff = query(ex, "difficulty");
            Integer majorId = (mid != null && !mid.isEmpty()) ? Integer.parseInt(mid) : null;
            QuestionTypeEnum qt = (type != null && !type.isEmpty()) ? QuestionTypeEnum.fromValue(type) : null;
            DifficultyEnum de = (diff != null && !diff.isEmpty()) ? DifficultyEnum.fromValue(diff) : null;
            List<Map<String, Object>> qlist = new ArrayList<>();
            for (Question q : questionDao.findAll(majorId, qt, de)) qlist.add(mapQuestion(q));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("data", qlist);
            ok(ex, r);
            return;
        }
        if (!needTeacher(ex)) return;
        if (method.equals("POST")) {
            Map<String, Object> b = body(ex);
            Question q = new Question();
            q.setContent((String) b.get("content"));
            Object opts = b.get("options");
            q.setOptions(opts instanceof List ? (List<String>) opts : new ArrayList<>());
            q.setAnswer((String) b.get("answer"));
            q.setAnalysis((String) b.get("analysis"));
            Object mid = b.get("majorId");
            q.setMajorId(mid instanceof Number ? ((Number) mid).intValue() : 0);
            try { q.setType(QuestionTypeEnum.fromValue((String) b.get("type"))); } catch (Exception e) { q.setType(QuestionTypeEnum.SINGLE_CHOICE); }
            try { q.setDifficulty(DifficultyEnum.fromValue((String) b.get("difficulty"))); } catch (Exception e) { q.setDifficulty(DifficultyEnum.MEDIUM); }
            if (q.getContent() == null || q.getContent().isEmpty()) { err(ex, 400, "content required"); return; }
            int id = questionDao.create(q);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", id);
            ok(ex, 201, r);
            return;
        }
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        if ((method.equals("PUT") || method.equals("DELETE")) && parts.length > 0) {
            int id;
            try { id = Integer.parseInt(parts[parts.length - 1]); } catch (Exception e) { err(ex, 400, "Invalid id"); return; }
            if (method.equals("PUT")) {
                Map<String, Object> b = body(ex);
                Question q = new Question();
                q.setId(id);
                q.setContent((String) b.get("content"));
                Object opts = b.get("options");
                q.setOptions(opts instanceof List ? (List<String>) opts : new ArrayList<>());
                q.setAnswer((String) b.get("answer"));
                q.setAnalysis((String) b.get("analysis"));
                Object mid = b.get("majorId");
                q.setMajorId(mid instanceof Number ? ((Number) mid).intValue() : 0);
                try { q.setType(QuestionTypeEnum.fromValue((String) b.get("type"))); } catch (Exception e) { q.setType(QuestionTypeEnum.SINGLE_CHOICE); }
                try { q.setDifficulty(DifficultyEnum.fromValue((String) b.get("difficulty"))); } catch (Exception e) { q.setDifficulty(DifficultyEnum.MEDIUM); }
                questionDao.update(q);
            } else {
                questionDao.delete(id);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", true);
            ok(ex, r);
            return;
        }
        err(ex, 405, "Method not allowed");
    }

    private Map<String, Object> mapQuestion(Question q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("content", q.getContent());
        m.put("options", q.getOptions() == null ? new ArrayList<>() : q.getOptions());
        m.put("answer", q.getAnswer());
        m.put("analysis", q.getAnalysis());
        m.put("majorId", q.getMajorId());
        m.put("majorName", q.getMajorName());
        m.put("type", q.getType() != null ? q.getType().getValue() : "single_choice");
        m.put("typeLabel", q.getType() != null ? q.getType().getLabel() : "单选题");
        m.put("difficulty", q.getDifficulty() != null ? q.getDifficulty().getValue() : "medium");
        m.put("difficultyLabel", q.getDifficulty() != null ? q.getDifficulty().getLabel() : "中等");
        m.put("createdAt", q.getCreatedAt() == null ? null : q.getCreatedAt().toString());
        m.put("isSubjective", q.getType() != null && q.getType().isSubjective());
        return m;
    }

    // ===== Exams =====
    private void handleExams(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        String last = parts.length > 2 ? parts[parts.length - 1] : "";

        if (method.equals("GET")) {
            if (!needLogin(ex)) return;
            User u = currentUser(ex);
            List<Exam> list = u.isTeacher() ? examDao.findAll() : examDao.findByStudentInvited(u.getId());
            List<Map<String, Object>> out = new ArrayList<>();
            for (Exam e : list) out.add(mapExam(e));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("data", out);
            ok(ex, r);
            return;
        }
        if (!needTeacher(ex)) return;

        if (method.equals("POST")) {
            if (parts.length >= 5 && parts[parts.length - 2].equals("questions")) {
                int examId = Integer.parseInt(parts[parts.length - 3]);
                Map<String, Object> b = body(ex);
                Object qid = b.get("questionId");
                int questionId = qid instanceof Number ? ((Number) qid).intValue() : Integer.parseInt(qid.toString());
                Object score = b.get("score");
                double s = score instanceof Number ? ((Number) score).doubleValue() : 10.0;
                examQuestionDao.addQuestion(examId, questionId, s, 0);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ok", true);
                ok(ex, r);
                return;
            }
            if (parts.length >= 5 && parts[parts.length - 2].equals("students")) {
                int examId = Integer.parseInt(parts[parts.length - 3]);
                Map<String, Object> b = body(ex);
                Object sid = b.get("studentId");
                int studentId = sid instanceof Number ? ((Number) sid).intValue() : Integer.parseInt(sid.toString());
                examStudentDao.invite(examId, studentId);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ok", true);
                ok(ex, r);
                return;
            }
            // create exam
            Map<String, Object> b = body(ex);
            Exam e = new Exam();
            e.setTitle((String) b.get("title"));
            e.setDescription((String) b.get("description"));
            Object dur = b.get("duration");
            e.setDuration(dur instanceof Number ? ((Number) dur).intValue() : 60);
            Object start = b.get("startTime");
            Object endT = b.get("endTime");
            if (start instanceof String && !((String) start).isEmpty()) {
                try { e.setStartTime(LocalDateTime.parse(((String) start).replace(" ", "T").substring(0, 19))); } catch (Exception ignored) {}
            }
            if (endT instanceof String && !((String) endT).isEmpty()) {
                try { e.setEndTime(LocalDateTime.parse(((String) endT).replace(" ", "T").substring(0, 19))); } catch (Exception ignored) {}
            }
            try { e.setStatus(ExamStatusEnum.fromValue((String) b.getOrDefault("status", "draft"))); } catch (Exception ex2) { e.setStatus(ExamStatusEnum.DRAFT); }
            e.setCreatorId(currentUser(ex).getId());
            if (e.getTitle() == null || e.getTitle().isEmpty()) { err(ex, 400, "title required"); return; }
            int id = examDao.create(e);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", id);
            ok(ex, 201, r);
            return;
        }

        if (method.equals("DELETE")) {
            if (parts.length >= 5 && parts[parts.length - 2].equals("questions")) {
                int examId = Integer.parseInt(parts[parts.length - 3]);
                int qid = Integer.parseInt(last);
                examQuestionDao.removeQuestion(examId, qid);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ok", true);
                ok(ex, r);
                return;
            }
            if (parts.length >= 5 && parts[parts.length - 2].equals("students")) {
                int examId = Integer.parseInt(parts[parts.length - 3]);
                int sid = Integer.parseInt(last);
                examStudentDao.remove(examId, sid);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ok", true);
                ok(ex, r);
                return;
            }
            if (parts.length >= 3 && !last.isEmpty()) {
                int id = Integer.parseInt(last);
                examDao.delete(id);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ok", true);
                ok(ex, r);
                return;
            }
        }

        if (method.equals("PUT") && parts.length >= 3 && !last.isEmpty()) {
            int id = Integer.parseInt(last);
            Map<String, Object> b = body(ex);
            Optional<Exam> opt = examDao.findById(id);
            if (!opt.isPresent()) { err(ex, 404, "Not found"); return; }
            Exam e = opt.get();
            if (b.containsKey("title")) e.setTitle((String) b.get("title"));
            if (b.containsKey("description")) e.setDescription((String) b.get("description"));
            if (b.containsKey("duration")) {
                Object d = b.get("duration");
                e.setDuration(d instanceof Number ? ((Number) d).intValue() : 60);
            }
            if (b.containsKey("status")) {
                try { e.setStatus(ExamStatusEnum.fromValue((String) b.get("status"))); } catch (Exception ignored) {}
            }
            Object start = b.get("startTime");
            Object endT = b.get("endTime");
            if (start instanceof String && !((String) start).isEmpty()) {
                try { e.setStartTime(LocalDateTime.parse(((String) start).replace(" ", "T").substring(0, 19))); } catch (Exception ignored) {}
            }
            if (endT instanceof String && !((String) endT).isEmpty()) {
                try { e.setEndTime(LocalDateTime.parse(((String) endT).replace(" ", "T").substring(0, 19))); } catch (Exception ignored) {}
            }
            examDao.update(e);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", true);
            ok(ex, r);
            return;
        }

        err(ex, 405, "Method not allowed");
    }

    private Map<String, Object> mapExam(Exam e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("description", e.getDescription());
        m.put("startTime", e.getStartTime() == null ? null : e.getStartTime().toString());
        m.put("endTime", e.getEndTime() == null ? null : e.getEndTime().toString());
        m.put("duration", e.getDuration());
        m.put("status", e.getStatus().getValue());
        m.put("creatorId", e.getCreatorId());
        m.put("questionCount", e.getQuestionCount());
        m.put("studentCount", e.getStudentCount());
        List<ExamQuestion> eqs = examQuestionDao.findByExamId(e.getId());
        List<Map<String, Object>> qs = new ArrayList<>();
        for (ExamQuestion eq : eqs) {
            Optional<Question> q = questionDao.findById(eq.getQuestionId());
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", eq.getQuestionId());
            qm.put("score", eq.getScore());
            qm.put("order", eq.getOrder());
            if (q.isPresent()) {
                qm.put("content", q.get().getContent());
                qm.put("type", q.get().getType().getValue());
                qm.put("typeLabel", q.get().getType().getLabel());
            }
            qs.add(qm);
        }
        m.put("questions", qs);
        List<ExamStudent> ess = examStudentDao.findByExamId(e.getId());
        List<Map<String, Object>> ss = new ArrayList<>();
        for (ExamStudent es : ess) {
            Optional<User> u = userDao.findById(es.getStudentId());
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", es.getStudentId());
            sm.put("username", u.isPresent() ? u.get().getUsername() : "unknown");
            ss.add(sm);
        }
        m.put("students", ss);
        return m;
    }

    // ===== Take Exam =====
    private void handleTakeSession(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needLogin(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        User u = currentUser(ex);
        Map<String, Object> b = body(ex);
        Object eid = b.get("examId");
        int examId = eid instanceof Number ? ((Number) eid).intValue() : Integer.parseInt(eid.toString());
        Optional<Exam> examOpt = examDao.findById(examId);
        if (!examOpt.isPresent()) { err(ex, 404, "Exam not found"); return; }
        Exam exam = examOpt.get();
        if (!examStudentDao.isInvited(examId, u.getId())) { err(ex, 403, "Not invited"); return; }
        if (exam.getStatus() == ExamStatusEnum.DRAFT) { err(ex, 403, "Exam not published"); return; }

        Optional<ExamSession> active = examSessionDao.findActive(examId, u.getId());
        int sessionId = active.isPresent() ? active.get().getId() : examSessionDao.create(examId, u.getId());
        List<Integer> qids = examQuestionDao.findQuestionIds(examId);
        List<Question> questions = questionDao.findByIds(qids);
        List<ExamQuestion> eqs = examQuestionDao.findByExamId(examId);
        Map<Integer, Double> scoreMap = new HashMap<>();
        for (ExamQuestion eq : eqs) scoreMap.put(eq.getQuestionId(), eq.getScore());

        List<Map<String, Object>> qlist = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.getId());
            qm.put("content", q.getContent());
            qm.put("options", q.getOptions());
            qm.put("type", q.getType().getValue());
            qm.put("typeLabel", q.getType().getLabel());
            qm.put("difficulty", q.getDifficulty().getValue());
            qm.put("score", scoreMap.getOrDefault(q.getId(), 10.0));
            Optional<Answer> prev = answerDao.findBySessionAndQuestion(sessionId, q.getId());
            qm.put("studentAnswer", prev.isPresent() ? prev.get().getStudentAnswer() : null);
            qlist.add(qm);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("exam", mapExam(exam));
        resp.put("questions", qlist);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", resp);
        ok(ex, r);
    }

    private void handleTakeAnswer(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needLogin(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        Object sid = b.get("sessionId");
        Object qid = b.get("questionId");
        int sessionId = sid instanceof Number ? ((Number) sid).intValue() : Integer.parseInt(sid.toString());
        int questionId = qid instanceof Number ? ((Number) qid).intValue() : Integer.parseInt(qid.toString());
        Answer a = new Answer();
        a.setSessionId(sessionId);
        a.setQuestionId(questionId);
        a.setStudentAnswer((String) b.get("studentAnswer"));
        answerDao.saveOrUpdate(a);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        ok(ex, r);
    }

    private void handleTakeSubmit(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needLogin(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        User u = currentUser(ex);
        Map<String, Object> b = body(ex);
        Object sid = b.get("sessionId");
        int sessionId = sid instanceof Number ? ((Number) sid).intValue() : Integer.parseInt(sid.toString());
        Object eid = b.get("examId");
        int examId = eid instanceof Number ? ((Number) eid).intValue() : Integer.parseInt(eid.toString());
        examSessionDao.updateStatus(sessionId, SessionStatusEnum.SUBMITTED);
        List<Integer> qids = examQuestionDao.findQuestionIds(examId);
        List<Question> qs = questionDao.findByIds(qids);
        boolean hasSub = aiService.gradeExamLocally(examId, sessionId, qs);
        double total = examQuestionDao.getTotalScore(examId);
        Result r = aiService.finalizeResult(examId, u.getId(), sessionId, total, hasSub);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("resultId", r.getId());
        resp.put("score", r.getScore());
        resp.put("totalScore", r.getTotalScore());
        resp.put("hasSubjective", hasSub);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", resp);
        ok(ex, out);
    }

    private void handleTakeSwitch(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needLogin(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        Object sid = b.get("sessionId");
        int sessionId = sid instanceof Number ? ((Number) sid).intValue() : Integer.parseInt(sid.toString());
        examSessionDao.incrementSwitchCount(sessionId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        ok(ex, r);
    }

    // ===== Results =====
    private void handleResults(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needLogin(ex)) return;
        User u = currentUser(ex);
        boolean isTeacher = u.isTeacher();
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (method.equals("GET")) {
            String last = parts.length > 2 ? parts[parts.length - 1] : "";
            if (last.isEmpty() || last.equals("results")) {
                List<Result> results = isTeacher ? resultDao.findAll() : resultDao.findByStudent(u.getId());
                List<Map<String, Object>> list = new ArrayList<>();
                for (Result r : results) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("examId", r.getExamId());
                    m.put("examTitle", r.getExamTitle());
                    m.put("studentId", r.getStudentId());
                    m.put("studentName", r.getStudentName());
                    m.put("score", r.getScore());
                    m.put("totalScore", r.getTotalScore());
                    m.put("submittedAt", r.getSubmittedAt() == null ? null : r.getSubmittedAt().toString());
                    m.put("aiAnalysis", r.getAiAnalysis());
                    list.add(m);
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("data", list);
                ok(ex, out);
                return;
            }
            int id = Integer.parseInt(last);
            Optional<Result> opt = resultDao.findById(id);
            if (!opt.isPresent()) { err(ex, 404, "Not found"); return; }
            Result r = opt.get();
            if (!isTeacher && r.getStudentId() != u.getId()) { err(ex, 403, "No permission"); return; }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", r.getId());
            data.put("examId", r.getExamId());
            data.put("examTitle", r.getExamTitle());
            data.put("studentId", r.getStudentId());
            data.put("studentName", r.getStudentName());
            data.put("score", r.getScore());
            data.put("totalScore", r.getTotalScore());
            data.put("submittedAt", r.getSubmittedAt() == null ? null : r.getSubmittedAt().toString());
            data.put("aiAnalysis", r.getAiAnalysis());

            int sid = getSessionIdByResult(r);
            List<Answer> answers = sid > 0 ? answerDao.findBySessionId(sid) : new ArrayList<>();
            List<Map<String, Object>> alist = new ArrayList<>();
            for (Answer a : answers) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("id", a.getId());
                am.put("questionId", a.getQuestionId());
                am.put("questionContent", a.getQuestionContent());
                am.put("questionType", a.getQuestionType());
                am.put("studentAnswer", a.getStudentAnswer());
                am.put("correctAnswer", a.getCorrectAnswer());
                am.put("isCorrect", a.getIsCorrect());
                am.put("effectiveScore", a.getEffectiveScore());
                am.put("manualScore", a.getManualScore());
                am.put("manualComment", a.getManualComment());
                am.put("needsManualGrade", a.isNeedsManualGrade());
                am.put("aiScore", a.getAiScore());
                am.put("aiAnalysis", a.getAiAnalysis());
                am.put("questionScore", a.getQuestionScore());
                alist.add(am);
            }
            data.put("answers", alist);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("data", data);
            ok(ex, out);
            return;
        }
        err(ex, 405, "Method not allowed");
    }

    private int getSessionIdByResult(Result r) {
        Optional<ExamSession> sess = examSessionDao.findActive(r.getExamId(), r.getStudentId());
        return sess.isPresent() ? sess.get().getId() : -1;
    }

    // ===== Grading =====
    private void handleGradingPending(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        List<Result> all = resultDao.findAll();
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Result r : all) {
            int sid = getSessionIdByResult(r);
            if (sid > 0 && answerDao.hasSubjectivePending(sid)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("resultId", r.getId());
                m.put("examTitle", r.getExamTitle());
                m.put("studentName", r.getStudentName());
                m.put("score", r.getScore());
                m.put("totalScore", r.getTotalScore());
                pending.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", pending);
        ok(ex, out);
    }

    private void handleGradingAnswer(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        Object aid = b.get("answerId");
        int answerId = aid instanceof Number ? ((Number) aid).intValue() : Integer.parseInt(aid.toString());
        Object ms = b.get("manualScore");
        double manualScore = ms instanceof Number ? ((Number) ms).doubleValue() : Double.parseDouble(ms.toString());
        String comment = (String) b.get("manualComment");
        answerDao.updateManualGrade(answerId, manualScore, comment);
        List<Result> all = resultDao.findAll();
        for (Result r : all) {
            int sid = getSessionIdByResult(r);
            if (sid > 0) {
                List<Answer> ans = answerDao.findBySessionId(sid);
                for (Answer a : ans) {
                    if (a.getId() == answerId) {
                        aiService.recalcResultScore(r.getId(), sid);
                        break;
                    }
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        ok(ex, out);
    }

    // ===== AI Generate =====
    private void handleAIGenerate(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        String major = (String) b.getOrDefault("major", "");
        String type = (String) b.getOrDefault("type", "single_choice");
        String difficulty = (String) b.getOrDefault("difficulty", "medium");
        String keywords = (String) b.getOrDefault("keywords", "");
        String hint = (String) b.getOrDefault("hint", "");
        Question q = aiService.generateOneQuestion(major, type, difficulty, keywords, hint);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", mapQuestion(q));
        ok(ex, out);
    }

    // ===== AI Grade Answer =====
    private void handleAIGrade(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        Object aid = b.get("answerId");
        int answerId = aid instanceof Number ? ((Number) aid).intValue() : Integer.parseInt(aid.toString());
        Map<String, Object> result = aiService.aiGradeAnswer(answerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", result);
        ok(ex, out);
    }

    // ===== AI Analyze Exam Result =====
    private void handleAIAnalyze(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        Object rid = b.get("resultId");
        int resultId = rid instanceof Number ? ((Number) rid).intValue() : Integer.parseInt(rid.toString());
        Map<String, Object> result = aiService.aiAnalyzeResult(resultId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", result);
        ok(ex, out);
    }

    // ===== AI Recommend =====
    private void handleAIRecommend(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        Map<String, Object> b = body(ex);
        String studentName = (String) b.getOrDefault("studentName", "学生");
        int count = b.get("count") instanceof Number ? ((Number) b.get("count")).intValue() : 5;

        @SuppressWarnings("unchecked")
        List<String> topics = b.get("topics") instanceof List
                ? (List<String>) b.get("topics")
                : new ArrayList<>();
        Map<String, Object> result = aiService.aiRecommend(studentName, topics, count);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", result);
        ok(ex, out);
    }

    // ===== Students list =====
    private void handleStudents(HttpExchange ex) throws IOException {
        if (isOptions(ex)) return;
        if (!needTeacher(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { err(ex, 405, "Method not allowed"); return; }
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : userDao.findAllStudents()) list.add(mapUser(u));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", list);
        ok(ex, out);
    }

    // ===== Simple JSON utility =====
    static class Json {
        static String escape(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            return sb.toString();
        }

        static String toJson(Object obj) {
            StringBuilder sb = new StringBuilder();
            append(sb, obj);
            return sb.toString();
        }

        private static void append(StringBuilder sb, Object obj) {
            if (obj == null) { sb.append("null"); return; }
            if (obj instanceof String) { sb.append('"').append(escape((String) obj)).append('"'); return; }
            if (obj instanceof Boolean) { sb.append(((Boolean) obj) ? "true" : "false"); return; }
            if (obj instanceof Number) { sb.append(obj.toString()); return; }
            if (obj instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(escape(String.valueOf(entry.getKey()))).append("\":");
                    append(sb, entry.getValue());
                }
                sb.append('}');
                return;
            }
            if (obj instanceof List) {
                sb.append('[');
                boolean first = true;
                for (Object item : (List<?>) obj) {
                    if (!first) sb.append(',');
                    first = false;
                    append(sb, item);
                }
                sb.append(']');
                return;
            }
            sb.append('"').append(escape(obj.toString())).append('"');
        }

        static Map<String, Object> parse(String s) {
            Parser p = new Parser(s);
            p.skipWs();
            Object obj = p.parseValue();
            if (obj instanceof Map) return (Map<String, Object>) obj;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", obj);
            return m;
        }

        static class Parser {
            final String src;
            int i = 0;
            Parser(String s) { this.src = s; }

            void skipWs() { while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++; }

            Object parseValue() {
                skipWs();
                if (i >= src.length()) return null;
                char c = src.charAt(i);
                if (c == '{') return parseObject();
                if (c == '[') return parseArray();
                if (c == '"') return parseString();
                if (c == 't' || c == 'f') return parseBool();
                if (c == 'n') { i += 4; return null; }
                return parseNumber();
            }

            Map<String, Object> parseObject() {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                skipWs();
                if (i < src.length() && src.charAt(i) == '}') { i++; return m; }
                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    if (i < src.length() && src.charAt(i) == ':') i++;
                    Object val = parseValue();
                    m.put(key, val);
                    skipWs();
                    if (i < src.length() && src.charAt(i) == ',') { i++; continue; }
                    if (i < src.length() && src.charAt(i) == '}') { i++; break; }
                    break;
                }
                return m;
            }

            List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                i++;
                skipWs();
                if (i < src.length() && src.charAt(i) == ']') { i++; return list; }
                while (true) {
                    Object val = parseValue();
                    list.add(val);
                    skipWs();
                    if (i < src.length() && src.charAt(i) == ',') { i++; continue; }
                    if (i < src.length() && src.charAt(i) == ']') { i++; break; }
                    break;
                }
                return list;
            }

            String parseString() {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < src.length()) {
                    char c = src.charAt(i++);
                    if (c == '"') break;
                    if (c == '\\' && i < src.length()) {
                        char nc = src.charAt(i++);
                        switch (nc) {
                            case '"': sb.append('"'); break;
                            case '\\': sb.append('\\'); break;
                            case '/': sb.append('/'); break;
                            case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break;
                            case 't': sb.append('\t'); break;
                            case 'b': sb.append('\b'); break;
                            case 'f': sb.append('\f'); break;
                            case 'u':
                                if (i + 4 <= src.length()) {
                                    try { sb.append((char) Integer.parseInt(src.substring(i, i + 4), 16)); } catch (Exception ignored) {}
                                    i += 4;
                                }
                                break;
                            default: sb.append(nc);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }

            Boolean parseBool() {
                if (src.startsWith("true", i)) { i += 4; return true; }
                if (src.startsWith("false", i)) { i += 5; return false; }
                return false;
            }

            Object parseNumber() {
                int start = i;
                boolean isDouble = false;
                while (i < src.length()) {
                    char c = src.charAt(i);
                    if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                        if (c == '.' || c == 'e' || c == 'E') isDouble = true;
                        i++;
                    } else break;
                }
                String num = src.substring(start, i);
                try {
                    if (isDouble) return Double.parseDouble(num);
                    long l = Long.parseLong(num);
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                    return l;
                } catch (Exception e) {
                    return 0;
                }
            }
        }
    }
}