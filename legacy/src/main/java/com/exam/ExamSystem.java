package com.exam;

import com.exam.ai.AIClient;
import com.exam.ai.AIService;
import com.exam.dao.*;
import com.exam.enums.*;
import com.exam.models.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ExamSystem {
    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

    private User currentUser;

    public void start() {
        System.out.println("\n===== 在线考试系统 (Java版) =====");
        while (true) {
            if (currentUser == null) {
                showAuthMenu();
            } else if (currentUser.isTeacher()) {
                showTeacherMenu();
            } else {
                showStudentMenu();
            }
        }
    }

    private void showAuthMenu() {
        System.out.println("\n--- 登录/注册 ---");
        System.out.println("1. 登录");
        System.out.println("2. 注册");
        System.out.println("3. 查看测试账号");
        System.out.println("0. 退出");
        String choice = input("请选择: ");
        switch (choice) {
            case "1": doLogin(); break;
            case "2": doRegister(); break;
            case "3": showTestAccounts(); break;
            case "0": System.out.println("再见!"); System.exit(0);
            default: System.out.println("无效选项");
        }
    }

    private void showTestAccounts() {
        System.out.println("\n测试账号（密码均为 123123）:");
        System.out.println("- 教师: nfpz");
        System.out.println("- 学生: 123 / student2");
    }

    private void doLogin() {
        String username = input("用户名: ");
        String password = input("密码: ");
        Optional<User> u = userDao.findByUsername(username);
        if (!u.isPresent()) {
            System.out.println("用户不存在");
            return;
        }
        if (!com.exam.database.DatabaseManager.checkPassword(password, u.get().getPasswordHash())) {
            System.out.println("密码错误");
            return;
        }
        currentUser = u.get();
        autoEndExpiredExams();
        System.out.println("登录成功！欢迎 " + currentUser.getUsername() + "(" + currentUser.getRole().getLabel() + ")");
    }

    private void doRegister() {
        String username = input("用户名: ");
        if (userDao.existsByUsername(username)) {
            System.out.println("用户名已存在");
            return;
        }
        String password = input("密码: ");
        String role = input("角色 (1=教师, 2=学生): ");
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(com.exam.database.DatabaseManager.hashPassword(password));
        u.setRole(role.equals("1") ? RoleEnum.TEACHER : RoleEnum.STUDENT);
        u.setEmail("");
        if (userDao.register(u)) {
            System.out.println("注册成功，请登录");
        } else {
            System.out.println("注册失败");
        }
    }

    private void logout() {
        currentUser = null;
        System.out.println("已退出登录");
    }

    // ---------- TEACHER ----------
    private void showTeacherMenu() {
        System.out.println("\n--- 教师控制台 ---");
        System.out.println("1. 题库管理");
        System.out.println("2. 考试管理");
        System.out.println("3. 人工评分");
        System.out.println("4. 成绩分析");
        System.out.println("5. AI智能出题");
        System.out.println("6. 专业管理");
        System.out.println("0. 退出登录");
        String choice = input("请选择: ");
        switch (choice) {
            case "1": questionBankMenu(); break;
            case "2": examManagementMenu(); break;
            case "3": manualGradingMenu(); break;
            case "4": resultAnalysisMenu(); break;
            case "5": aiGenerateMenu(); break;
            case "6": majorMenu(); break;
            case "0": logout(); break;
            default: System.out.println("无效选项");
        }
    }

    private void majorMenu() {
        while (true) {
            System.out.println("\n--- 专业管理 ---");
            List<Major> majors = majorDao.findAll();
            for (Major m : majors) {
                int qc = majorDao.countQuestions(m.getId());
                System.out.println(String.format("[%d] %s - %s (题目数: %d)", m.getId(), m.getName(),
                        m.getDescription() == null ? "" : m.getDescription(), qc));
            }
            System.out.println("\na. 添加专业  d. 删除专业  b. 返回");
            String c = input("请选择: ");
            if (c.equalsIgnoreCase("a")) {
                String name = input("专业名称: ");
                String desc = input("专业描述: ");
                Major m = new Major(name, desc);
                int id = majorDao.create(m);
                if (id > 0) System.out.println("添加成功 ID=" + id);
            } else if (c.equalsIgnoreCase("d")) {
                int id = Integer.parseInt(input("要删除的专业ID: "));
                int qc = majorDao.countQuestions(id);
                if (qc > 0) {
                    System.out.println("该专业下还有 " + qc + " 道题目，是否继续删除(y/n)? ");
                    String yn = scanner.nextLine();
                    if (!yn.equalsIgnoreCase("y")) continue;
                }
                if (majorDao.delete(id)) System.out.println("删除成功");
                else System.out.println("删除失败");
            } else if (c.equalsIgnoreCase("b")) {
                return;
            }
        }
    }

    private void questionBankMenu() {
        while (true) {
            System.out.println("\n--- 题库管理 ---");
            List<Question> qs = questionDao.findAll(null, null, null);
            if (qs.isEmpty()) {
                System.out.println("题库为空");
            } else {
                for (Question q : qs) {
                    System.out.println(String.format("[%d] [%s-%s] %s", q.getId(),
                            q.getType().getLabel(), q.getDifficulty().getLabel(),
                            q.getContent().length() > 50 ? q.getContent().substring(0, 50) + "..." : q.getContent()));
                }
            }
            System.out.println("\na. 添加题目  e. 编辑题目  d. 删除题目  f. 筛选  b. 返回");
            String c = input("请选择: ");
            if (c.equalsIgnoreCase("a")) addQuestion();
            else if (c.equalsIgnoreCase("e")) editQuestion();
            else if (c.equalsIgnoreCase("d")) deleteQuestion();
            else if (c.equalsIgnoreCase("f")) filterQuestions();
            else if (c.equalsIgnoreCase("b")) return;
        }
    }

    private void addQuestion() {
        System.out.println("\n题型: 1.单选 2.多选 3.填空 4.判断 5.简答 6.编程 7.应用 8.计算");
        int t = Integer.parseInt(input("选择题型: "));
        QuestionTypeEnum type = Arrays.stream(QuestionTypeEnum.values()).skip(t - 1).findFirst().orElse(QuestionTypeEnum.SINGLE_CHOICE);
        String d = input("难度(1.简单 2.中等 3.困难): ");
        DifficultyEnum diff = d.equals("1") ? DifficultyEnum.EASY : d.equals("3") ? DifficultyEnum.HARD : DifficultyEnum.MEDIUM;
        String content = input("题目内容: ");
        String answer = input("参考答案: ");
        String analysis = input("题目解析: ");
        List<String> options = new ArrayList<>();
        if (type == QuestionTypeEnum.SINGLE_CHOICE || type == QuestionTypeEnum.MULTIPLE_CHOICE) {
            for (int i = 0; i < 4; i++) {
                options.add(input("选项" + (char) ('A' + i) + ": "));
            }
        }
        List<Major> majors = majorDao.findAll();
        for (Major m : majors) System.out.println("[" + m.getId() + "] " + m.getName());
        int mid = Integer.parseInt(input("所属专业ID: "));

        Question q = new Question();
        q.setContent(content);
        q.setAnswer(answer);
        q.setAnalysis(analysis);
        q.setOptions(options);
        q.setMajorId(mid);
        q.setType(type);
        q.setDifficulty(diff);
        int id = questionDao.create(q);
        if (id > 0) System.out.println("题目已添加 ID=" + id);
    }

    private void editQuestion() {
        int id = Integer.parseInt(input("要编辑的题目ID: "));
        Optional<Question> opt = questionDao.findById(id);
        if (!opt.isPresent()) {
            System.out.println("题目不存在");
            return;
        }
        Question q = opt.get();
        System.out.println("原内容: " + q.getContent());
        String newContent = input("新内容(回车保留原值): ");
        if (!newContent.isEmpty()) q.setContent(newContent);
        String newAnswer = input("新答案(回车保留原值): ");
        if (!newAnswer.isEmpty()) q.setAnswer(newAnswer);
        if (questionDao.update(q)) System.out.println("更新成功");
    }

    private void deleteQuestion() {
        int id = Integer.parseInt(input("要删除的题目ID: "));
        String confirm = input("确认删除? (y/n): ");
        if (confirm.equalsIgnoreCase("y")) {
            if (questionDao.delete(id)) System.out.println("删除成功");
        }
    }

    private void filterQuestions() {
        Integer mid = null;
        QuestionTypeEnum type = null;
        String sm = input("按专业ID筛选(回车跳过): ");
        if (!sm.isEmpty()) mid = Integer.parseInt(sm);
        String st = input("按题型筛选(1.单选...8.计算，回车跳过): ");
        if (!st.isEmpty()) type = Arrays.stream(QuestionTypeEnum.values()).skip(Integer.parseInt(st) - 1).findFirst().orElse(null);
        List<Question> qs = questionDao.findAll(mid, type, null);
        System.out.println("\n--- 筛选结果 ---");
        for (Question q : qs) {
            System.out.println(String.format("[%d] [%s-%s] %s | 答案: %s", q.getId(),
                    q.getType().getLabel(), q.getDifficulty().getLabel(), q.getContent(), q.getAnswer()));
        }
        input("按回车继续...");
    }

    private void aiGenerateMenu() {
        System.out.println("\n--- AI智能出题 ---");
        List<Major> majors = majorDao.findAll();
        for (Major m : majors) System.out.println("[" + m.getId() + "] " + m.getName());
        int mid = Integer.parseInt(input("专业ID: "));
        Optional<Major> major = majorDao.findById(mid);
        if (!major.isPresent()) {
            System.out.println("专业不存在");
            return;
        }
        System.out.println("题型: 1.单选 2.多选 3.填空 4.判断 5.简答 6.编程 7.应用 8.计算");
        int t = Integer.parseInt(input("选择题型: "));
        QuestionTypeEnum type = Arrays.stream(QuestionTypeEnum.values()).skip(t - 1).findFirst().orElse(QuestionTypeEnum.SINGLE_CHOICE);
        String d = input("难度(1.简单 2.中等 3.困难): ");
        String diff = d.equals("1") ? "简单" : d.equals("3") ? "困难" : "中等";
        int count = Integer.parseInt(input("生成数量: "));
        for (int i = 0; i < count; i++) {
            System.out.println("正在生成第 " + (i + 1) + "/" + count + " 道...");
            DifficultyEnum dEnum = DifficultyEnum.fromValue(d.equals("1") ? "easy" : d.equals("3") ? "hard" : "medium");
            Question q = aiService.generateOneQuestion(major.get().getName(), type.getValue(), dEnum.getValue());
            if (q != null) {
                q.setMajorId(mid);
                int id = questionDao.create(q);
                System.out.println("已入库 ID=" + id + ": " + q.getContent());
            } else {
                System.out.println("生成失败");
            }
        }
    }

    private void examManagementMenu() {
        while (true) {
            System.out.println("\n--- 考试管理 ---");
            List<Exam> exams = examDao.findByCreator(currentUser.getId());
            if (exams.isEmpty()) {
                System.out.println("暂无考试");
            } else {
                for (Exam e : exams) {
                    System.out.println(String.format("[%d] [%s] %s | 题目数: %d 学生数: %d",
                            e.getId(), e.getStatus().getLabel(), e.getTitle(),
                            e.getQuestionCount(), e.getStudentCount()));
                }
            }
            System.out.println("\nc. 创建考试  e. 编辑  q. 管理题目  s. 邀请学生  p. 发布  x. 结束  v. 查看成绩  b. 返回");
            String c = input("请选择: ");
            if (c.equalsIgnoreCase("c")) createExam();
            else if (c.equalsIgnoreCase("e")) editExam();
            else if (c.equalsIgnoreCase("q")) manageExamQuestions();
            else if (c.equalsIgnoreCase("s")) inviteStudents();
            else if (c.equalsIgnoreCase("p")) publishExam();
            else if (c.equalsIgnoreCase("x")) endExam();
            else if (c.equalsIgnoreCase("v")) viewExamResults();
            else if (c.equalsIgnoreCase("b")) return;
        }
    }

    private void createExam() {
        String title = input("考试标题: ");
        String desc = input("考试描述: ");
        int duration = Integer.parseInt(input("考试时长(分钟): "));
        LocalDateTime start = LocalDateTime.parse(input("开始时间(yyyy-MM-dd HH:mm): ") + ":00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime end = LocalDateTime.parse(input("截止时间(yyyy-MM-dd HH:mm): ") + ":00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Exam e = new Exam();
        e.setTitle(title);
        e.setDescription(desc);
        e.setDuration(duration);
        e.setStartTime(start);
        e.setEndTime(end);
        e.setStatus(ExamStatusEnum.DRAFT);
        e.setCreatorId(currentUser.getId());
        int id = examDao.create(e);
        if (id > 0) System.out.println("考试创建成功 ID=" + id);
    }

    private void editExam() {
        int id = Integer.parseInt(input("编辑的考试ID: "));
        Optional<Exam> opt = examDao.findById(id);
        if (!opt.isPresent()) {
            System.out.println("考试不存在");
            return;
        }
        Exam e = opt.get();
        if (e.getCreatorId() != currentUser.getId()) {
            System.out.println("无权操作");
            return;
        }
        String title = input("新标题(回车保留): ");
        if (!title.isEmpty()) e.setTitle(title);
        String dur = input("新时长(分钟, 回车保留): ");
        if (!dur.isEmpty()) e.setDuration(Integer.parseInt(dur));
        if (examDao.update(e)) System.out.println("更新成功");
    }

    private void manageExamQuestions() {
        int examId = Integer.parseInt(input("考试ID: "));
        Optional<Exam> examOpt = examDao.findById(examId);
        if (!examOpt.isPresent() || examOpt.get().getCreatorId() != currentUser.getId()) {
            System.out.println("无权操作或考试不存在");
            return;
        }
        while (true) {
            System.out.println("\n当前考试题目:");
            List<ExamQuestion> eqs = examQuestionDao.findByExamId(examId);
            for (ExamQuestion eq : eqs) {
                System.out.println(String.format("  [%d] %s (分值: %.0f)", eq.getQuestionId(), eq.getQuestionContent(), eq.getScore()));
            }
            System.out.println("\na. 从题库添加  r. 移除题目  s. 随机打乱  b. 返回");
            String c = input("请选择: ");
            if (c.equalsIgnoreCase("a")) {
                List<Question> qs = questionDao.findAll(null, null, null);
                System.out.println("\n可选题目:");
                for (Question q : qs) {
                    System.out.println(String.format("[%d] [%s] %s", q.getId(), q.getType().getLabel(), q.getContent()));
                }
                String ids = input("输入要添加的题目ID(逗号分隔): ");
                String[] arr = ids.split(",");
                int order = eqs.size() + 1;
                double score = Double.parseDouble(input("每题分值: "));
                for (String s : arr) {
                    int qid = Integer.parseInt(s.trim());
                    if (examQuestionDao.addQuestion(examId, qid, score, order++)) {
                        System.out.println("已添加 " + qid);
                    }
                }
            } else if (c.equalsIgnoreCase("r")) {
                int qid = Integer.parseInt(input("要移除的题目ID: "));
                if (examQuestionDao.removeQuestion(examId, qid)) {
                    examQuestionDao.reorder(examId);
                    System.out.println("已移除");
                }
            } else if (c.equalsIgnoreCase("s")) {
                List<ExamQuestion> list = examQuestionDao.findByExamId(examId);
                List<Integer> qids = list.stream().map(ExamQuestion::getQuestionId).collect(Collectors.toList());
                Collections.shuffle(qids);
                for (ExamQuestion eq : list) examQuestionDao.removeQuestion(examId, eq.getQuestionId());
                for (int i = 0; i < qids.size(); i++) examQuestionDao.addQuestion(examId, qids.get(i), 10, i + 1);
                System.out.println("已随机打乱");
            } else if (c.equalsIgnoreCase("b")) return;
        }
    }

    private void inviteStudents() {
        int examId = Integer.parseInt(input("考试ID: "));
        Optional<Exam> examOpt = examDao.findById(examId);
        if (!examOpt.isPresent() || examOpt.get().getCreatorId() != currentUser.getId()) {
            System.out.println("无权操作");
            return;
        }
        List<User> students = userDao.findAllStudents();
        System.out.println("\n学生列表:");
        for (User s : students) System.out.println("[" + s.getId() + "] " + s.getUsername());
        System.out.println("当前已邀请:");
        for (ExamStudent es : examStudentDao.findByExamId(examId)) System.out.println("  " + es.getStudentName());
        String ids = input("输入邀请的学生ID(逗号分隔,或输入 r+ID 移除): ");
        String[] arr = ids.split(",");
        for (String s : arr) {
            s = s.trim();
            if (s.toLowerCase().startsWith("r")) {
                int sid = Integer.parseInt(s.substring(1));
                examStudentDao.remove(examId, sid);
                System.out.println("已移除学生 " + sid);
            } else {
                int sid = Integer.parseInt(s);
                examStudentDao.invite(examId, sid);
                System.out.println("已邀请学生 " + sid);
            }
        }
    }

    private void publishExam() {
        int id = Integer.parseInt(input("发布的考试ID: "));
        Optional<Exam> opt = examDao.findById(id);
        if (!opt.isPresent()) { System.out.println("不存在"); return; }
        if (opt.get().getCreatorId() != currentUser.getId()) { System.out.println("无权"); return; }
        if (examQuestionDao.countByExam(id) == 0) {
            System.out.println("该考试还没有题目,无法发布");
            return;
        }
        if (examDao.updateStatus(id, ExamStatusEnum.PUBLISHED)) System.out.println("已发布");
    }

    private void endExam() {
        int id = Integer.parseInt(input("结束的考试ID: "));
        Optional<Exam> opt = examDao.findById(id);
        if (!opt.isPresent()) { System.out.println("不存在"); return; }
        if (opt.get().getCreatorId() != currentUser.getId()) { System.out.println("无权"); return; }
        if (examDao.updateStatus(id, ExamStatusEnum.ENDED)) {
            System.out.println("已结束考试");
            // 对未提交的会话进行自动批改
            List<ExamSession> sessions = examSessionDao.findActiveSessionsByExam(id);
            for (ExamSession session : sessions) {
                examSessionDao.updateStatus(session.getId(), SessionStatusEnum.SUBMITTED);
                List<Integer> qids = examQuestionDao.findQuestionIds(id);
                List<Question> qs = questionDao.findByIds(qids);
                aiService.gradeExamLocally(id, session.getId(), qs);
                double total = examQuestionDao.getTotalScore(id);
                aiService.finalizeResult(id, session.getStudentId(), session.getId(), total, answerDao.hasSubjectivePending(session.getId()));
            }
            System.out.println("已为 " + sessions.size() + " 个会话自动批改");
        }
    }

    private void viewExamResults() {
        int id = Integer.parseInt(input("考试ID: "));
        List<Result> results = resultDao.findByExam(id);
        if (results.isEmpty()) {
            System.out.println("暂无成绩");
            return;
        }
        System.out.println("\n--- 成绩列表 ---");
        for (Result r : results) {
            System.out.println(String.format("[%d] %s 得分: %.0f/%.0f  提交时间: %s",
                    r.getId(), r.getStudentName(), r.getScore(), r.getTotalScore(),
                    r.getSubmittedAt() == null ? "-" : r.getSubmittedAt().format(dtf)));
        }
    }

    private void manualGradingMenu() {
        System.out.println("\n--- 人工评分 ---");
        List<Exam> exams = examDao.findByCreator(currentUser.getId());
        for (Exam e : exams) {
            if (e.getStatus() == ExamStatusEnum.ENDED || e.getStatus() == ExamStatusEnum.PUBLISHED)
                System.out.println("[" + e.getId() + "] " + e.getTitle());
        }
        int examId = Integer.parseInt(input("选择考试ID: "));
        List<Result> results = resultDao.findByExam(examId);
        if (results.isEmpty()) {
            System.out.println("暂无成绩");
            return;
        }
        System.out.println("\n学生成绩:");
        for (Result r : results) {
            System.out.println(String.format("[%d] %s - %.0f/%.0f", r.getId(), r.getStudentName(), r.getScore(), r.getTotalScore()));
        }
        int resultId = Integer.parseInt(input("选择要评分的成绩ID: "));
        Optional<Result> ropt = resultDao.findById(resultId);
        if (!ropt.isPresent()) return;
        Result r = ropt.get();
        Optional<ExamSession> sess = examSessionDao.findActive(examId, r.getStudentId());
        int sessionId = -1;
        if (sess.isPresent()) sessionId = sess.get().getId();
        // 回查提交过的会话
        List<Answer> answers = answerDao.findBySessionId(sessionId);
        for (Answer a : answers) {
            if (a.getQuestionType() == null) continue;
            QuestionTypeEnum qt;
            try { qt = QuestionTypeEnum.fromValue(a.getQuestionType()); } catch (Exception ex) { continue; }
            if (!qt.isSubjective()) continue;
            System.out.println("\n题目: " + a.getQuestionContent());
            System.out.println("学生答案: " + (a.getStudentAnswer() == null ? "(未作答)" : a.getStudentAnswer()));
            System.out.println("参考答案: " + a.getCorrectAnswer());
            System.out.println("AI评分: " + a.getScore() + " / 当前有效: " + a.getEffectiveScore() + "(满分 " + a.getQuestionScore() + ")");
            String ms = input("人工评分(回车保留AI分): ");
            if (!ms.isEmpty()) {
                double score = Double.parseDouble(ms);
                String comment = input("评语(回车跳过): ");
                answerDao.updateManualGrade(a.getId(), score, comment);
            }
        }
        aiService.recalcResultScore(resultId, sessionId);
        System.out.println("评分完成，总成绩已更新");
    }

    private void resultAnalysisMenu() {
        System.out.println("\n--- 成绩分析 ---");
        List<Exam> exams = examDao.findByCreator(currentUser.getId());
        for (Exam e : exams) System.out.println("[" + e.getId() + "] " + e.getTitle());
        int examId = Integer.parseInt(input("考试ID: "));
        List<Result> results = resultDao.findByExam(examId);
        if (results.isEmpty()) {
            System.out.println("暂无成绩数据");
            return;
        }
        double sum = results.stream().mapToDouble(Result::getScore).sum();
        double avg = sum / results.size();
        double max = results.stream().mapToDouble(Result::getScore).max().orElse(0);
        double min = results.stream().mapToDouble(Result::getScore).min().orElse(0);
        System.out.println(String.format("参考人数: %d | 平均分: %.2f | 最高分: %.0f | 最低分: %.0f",
                results.size(), avg, max, min));
        int[] buckets = new int[5];
        for (Result r : results) {
            double pct = r.getTotalScore() > 0 ? r.getScore() / r.getTotalScore() : 0;
            int idx = (int) Math.min(4, Math.floor(pct * 5));
            buckets[idx]++;
        }
        System.out.println("\n分数段分布:");
        String[] labels = {"0-20%", "20-40%", "40-60%", "60-80%", "80-100%"};
        for (int i = 0; i < 5; i++) {
            System.out.println("  " + labels[i] + ": " + buckets[i] + " 人");
        }
        input("按回车继续...");
    }

    // ---------- STUDENT ----------
    private void showStudentMenu() {
        System.out.println("\n--- 学生控制台 ---");
        System.out.println("1. 我的考试 (待参加)");
        System.out.println("2. 查看我的成绩");
        System.out.println("0. 退出登录");
        String choice = input("请选择: ");
        switch (choice) {
            case "1": takeExamMenu(); break;
            case "2": viewMyResults(); break;
            case "0": logout(); break;
            default: System.out.println("无效选项");
        }
    }

    private void takeExamMenu() {
        System.out.println("\n--- 我的考试 ---");
        List<Exam> published = examDao.findByStudentInvited(currentUser.getId(), ExamStatusEnum.PUBLISHED);
        List<Exam> ended = examDao.findByStudentInvited(currentUser.getId(), ExamStatusEnum.ENDED);

        System.out.println("\n[可参加的考试]");
        if (published.isEmpty()) System.out.println("  (暂无)");
        for (Exam e : published) {
            System.out.println(String.format("[%d] %s  时长:%d分钟  题目数:%d",
                    e.getId(), e.getTitle(), e.getDuration(), e.getQuestionCount()));
        }
        System.out.println("\n[已结束的考试]");
        if (ended.isEmpty()) System.out.println("  (暂无)");
        for (Exam e : ended) {
            System.out.println(String.format("[%d] %s", e.getId(), e.getTitle()));
        }

        String id = input("\n输入考试ID进入答题(回车返回): ");
        if (id.isEmpty()) return;
        int examId = Integer.parseInt(id);
        Optional<Exam> examOpt = examDao.findById(examId);
        if (!examOpt.isPresent()) {
            System.out.println("考试不存在");
            return;
        }
        if (!examStudentDao.isInvited(examId, currentUser.getId())) {
            System.out.println("未被邀请");
            return;
        }
        Exam exam = examOpt.get();
        if (exam.getStatus() == ExamStatusEnum.DRAFT) {
            System.out.println("考试尚未发布");
            return;
        }
        if (exam.getStatus() == ExamStatusEnum.ENDED) {
            Optional<Result> r = resultDao.findByExamAndStudent(examId, currentUser.getId());
            if (r.isPresent()) {
                showResultDetail(r.get());
            } else {
                System.out.println("考试已结束");
            }
            return;
        }
        // 进行中
        takeExam(exam);
    }

    private void takeExam(Exam exam) {
        Optional<ExamSession> existing = examSessionDao.findActive(exam.getId(), currentUser.getId());
        int sessionId;
        if (existing.isPresent()) {
            sessionId = existing.get().getId();
            System.out.println("已恢复上一次的答题会话");
        } else {
            sessionId = examSessionDao.create(exam.getId(), currentUser.getId());
            System.out.println("开始考试！会话ID=" + sessionId);
        }
        List<Integer> qids = examQuestionDao.findQuestionIds(exam.getId());
        List<Question> qs = questionDao.findByIds(qids);
        if (qs.isEmpty()) {
            System.out.println("该考试没有题目");
            return;
        }

        // 切屏计数演示
        int switchCount = 0;
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(exam.getDuration());

        int idx = 0;
        while (idx < qs.size()) {
            Question q = qs.get(idx);
            System.out.println("\n=== 第 " + (idx + 1) + "/" + qs.size() + " 题 ===");
            System.out.println("[" + q.getType().getLabel() + " " + q.getDifficulty().getLabel() + "] " + q.getContent());
            if (q.getOptions() != null && !q.getOptions().isEmpty()) {
                for (int i = 0; i < q.getOptions().size(); i++) {
                    System.out.println("  " + (char) ('A' + i) + ". " + q.getOptions().get(i));
                }
            }
            Optional<Answer> prev = answerDao.findBySessionAndQuestion(sessionId, q.getId());
            String current = prev.isPresent() && prev.get().getStudentAnswer() != null ? prev.get().getStudentAnswer() : "";
            if (!current.isEmpty()) System.out.println("上次作答: " + current);

            System.out.println("\nn. 下一题  p. 上一题  s. 提交答卷  c. 模拟切屏  q. 退出");
            String ans = input("输入作答或命令: ");
            if (ans.equalsIgnoreCase("n")) {
                idx = Math.min(qs.size() - 1, idx + 1);
            } else if (ans.equalsIgnoreCase("p")) {
                idx = Math.max(0, idx - 1);
            } else if (ans.equalsIgnoreCase("s")) {
                if (submitExam(exam, sessionId, qs)) {
                    return;
                }
            } else if (ans.equalsIgnoreCase("c")) {
                switchCount++;
                examSessionDao.incrementSwitchCount(sessionId);
                System.out.println("⚠ 切屏警告: 已切屏 " + switchCount + " 次");
                if (switchCount >= 3) {
                    System.out.println("⚠ 达到3次切屏，强制提交答卷！");
                    submitExam(exam, sessionId, qs);
                    return;
                }
            } else if (ans.equalsIgnoreCase("q")) {
                return;
            } else {
                Answer a = new Answer();
                a.setSessionId(sessionId);
                a.setQuestionId(q.getId());
                a.setStudentAnswer(ans);
                answerDao.saveOrUpdate(a);
                System.out.println("已保存作答");
                idx = Math.min(qs.size() - 1, idx + 1);
            }

            if (LocalDateTime.now().isAfter(endTime)) {
                System.out.println("⏰ 考试时间到，自动提交！");
                submitExam(exam, sessionId, qs);
                return;
            }
        }
        // 答完所有题
        String submit = input("已到达最后一题，是否提交? (y/n): ");
        if (submit.equalsIgnoreCase("y")) {
            submitExam(exam, sessionId, qs);
        }
    }

    private boolean submitExam(Exam exam, int sessionId, List<Question> qs) {
        examSessionDao.updateStatus(sessionId, SessionStatusEnum.SUBMITTED);
        boolean hasSub = aiService.gradeExamLocally(exam.getId(), sessionId, qs);
        double total = examQuestionDao.getTotalScore(exam.getId());
        Result r = aiService.finalizeResult(exam.getId(), currentUser.getId(), sessionId, total, hasSub);
        System.out.println("\n=== 答卷已提交 ===");
        System.out.println("成绩: " + r.getScore() + " / " + r.getTotalScore());
        if (hasSub) System.out.println("提示: 存在主观题，将由教师人工评分后更新最终成绩");
        input("按回车继续...");
        return true;
    }

    private void viewMyResults() {
        System.out.println("\n--- 我的成绩 ---");
        List<Result> results = resultDao.findByStudent(currentUser.getId());
        if (results.isEmpty()) {
            System.out.println("暂无成绩");
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            System.out.println(String.format("[%d] %s  得分: %.0f/%.0f  时间: %s",
                    i + 1, r.getExamTitle(), r.getScore(), r.getTotalScore(),
                    r.getSubmittedAt() == null ? "-" : r.getSubmittedAt().format(dtf)));
        }
        String idx = input("\n选择编号查看详情(回车返回): ");
        if (idx.isEmpty()) return;
        try {
            Result r = results.get(Integer.parseInt(idx) - 1);
            showResultDetail(r);
        } catch (Exception e) {
            System.out.println("无效编号");
        }
    }

    private void showResultDetail(Result r) {
        System.out.println("\n=== 成绩详情: " + r.getExamTitle() + " ===");
        System.out.println("得分: " + r.getScore() + " / " + r.getTotalScore());
        System.out.println("提交时间: " + (r.getSubmittedAt() == null ? "-" : r.getSubmittedAt().format(dtf)));
        System.out.println("备注: " + (r.getAiAnalysis() == null ? "-" : r.getAiAnalysis()));

        // 展示答题详情
        Optional<ExamSession> sess = examSessionDao.findActive(r.getExamId(), r.getStudentId());
        int sessionId = -1;
        if (sess.isPresent()) sessionId = sess.get().getId();
        // 查找最近一次提交会话（这里简化: 若没找到活跃的，也没有历史记录接口）
        if (sessionId > 0) {
            List<Answer> answers = answerDao.findBySessionId(sessionId);
            System.out.println("\n[答题详情]");
            for (int i = 0; i < answers.size(); i++) {
                Answer a = answers.get(i);
                System.out.println(String.format("Q%d (%s) | 学生: %s | 参考: %s | 得分: %.0f/%.0f%s",
                        i + 1, a.getQuestionType() == null ? "" : a.getQuestionType(),
                        a.getStudentAnswer() == null ? "(空)" : a.getStudentAnswer(),
                        a.getCorrectAnswer() == null ? "-" : a.getCorrectAnswer(),
                        a.getEffectiveScore(), a.getQuestionScore(),
                        a.isNeedsManualGrade() ? " [待人工评分]" : ""));
            }
        }
        input("按回车继续...");
    }

    private void autoEndExpiredExams() {
        List<Exam> expired = examDao.findExpiredPublished();
        if (expired.isEmpty()) return;
        for (Exam e : expired) {
            examDao.updateStatus(e.getId(), ExamStatusEnum.ENDED);
            List<ExamSession> sessions = examSessionDao.findActiveSessionsByExam(e.getId());
            for (ExamSession s : sessions) {
                examSessionDao.updateStatus(s.getId(), SessionStatusEnum.SUBMITTED);
                List<Integer> qids = examQuestionDao.findQuestionIds(e.getId());
                List<Question> qs = questionDao.findByIds(qids);
                aiService.gradeExamLocally(e.getId(), s.getId(), qs);
                double total = examQuestionDao.getTotalScore(e.getId());
                aiService.finalizeResult(e.getId(), s.getStudentId(), s.getId(), total, answerDao.hasSubjectivePending(s.getId()));
            }
        }
        if (!expired.isEmpty()) System.out.println("系统自动结束了 " + expired.size() + " 个过期考试");
    }

    private String input(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }
}