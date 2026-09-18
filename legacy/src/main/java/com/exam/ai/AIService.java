package com.exam.ai;

import com.exam.dao.*;
import com.exam.enums.QuestionTypeEnum;
import com.exam.enums.DifficultyEnum;
import com.exam.models.*;

import java.util.*;

public class AIService {
    private final AIClient client;
    private final AnswerDao answerDao = new AnswerDao();
    private final ExamQuestionDao examQuestionDao = new ExamQuestionDao();
    private final ResultDao resultDao = new ResultDao();
    private final MajorDao majorDao = new MajorDao();

    public AIService(AIClient client) {
        this.client = client;
    }

    public Question generateOneQuestion(String major, String type, String difficulty) {
        return generateOneQuestion(major, type, difficulty, "", "");
    }

    public Question generateOneQuestion(String major, String type, String difficulty, String keywords, String hint) {
        QuestionTypeEnum t;
        try { t = QuestionTypeEnum.fromValue(type); } catch (Exception e) { t = QuestionTypeEnum.SINGLE_CHOICE; }
        DifficultyEnum d;
        try { d = DifficultyEnum.fromValue(difficulty); } catch (Exception e) { d = DifficultyEnum.MEDIUM; }
        Question q = generateOneQuestion(major, t, d, keywords, hint);
        if (major != null && !major.isEmpty()) {
            Optional<Major> m = majorDao.findByName(major);
            if (m.isPresent()) {
                q.setMajorId(m.get().getId());
                q.setMajorName(m.get().getName());
            } else {
                q.setMajorName(major);
            }
        }
        return q;
    }

    public Question generateOneQuestion(String major, QuestionTypeEnum type, DifficultyEnum difficulty) {
        return generateOneQuestion(major, type, difficulty, "", "");
    }

    public Question generateOneQuestion(String major, QuestionTypeEnum type, DifficultyEnum difficulty,
                                     String keywords, String hint) {
        Map<String, Object> resp = client.generateQuestion(major, type.getValue(), difficulty.getValue(), keywords, hint);
        Question q = new Question();
        q.setContent((String) resp.get("content"));
        q.setAnswer((String) resp.get("answer"));
        q.setAnalysis((String) resp.get("analysis"));
        Object opts = resp.get("options");
        if (opts instanceof List) {
            q.setOptions((List<String>) opts);
        } else {
            q.setOptions(new ArrayList<>());
        }
        q.setType(type);
        q.setDifficulty(difficulty);
        return q;
    }

    public boolean gradeExamLocally(int examId, int sessionId, List<Question> questions) {
        boolean anySubjective = false;
        for (Question q : questions) {
            Optional<Answer> existingOpt = answerDao.findBySessionAndQuestion(sessionId, q.getId());
            Answer existing = existingOpt.orElse(null);
            Answer answer = existing != null ? existing : new Answer();
            answer.setSessionId(sessionId);
            answer.setQuestionId(q.getId());
            if (answer.getStudentAnswer() == null) answer.setStudentAnswer("");

            if (q.getType().isSubjective()) {
                anySubjective = true;
                answer.setScore(0.0);
                answer.setIsCorrect(false);
                answer.setNeedsManualGrade(true);
            } else {
                boolean correct = compareObjectiveAnswer(answer.getStudentAnswer(), q.getAnswer(), q.getType());
                double score = correct ? getQuestionScore(examId, q.getId()) : 0;
                answer.setScore(score);
                answer.setIsCorrect(correct);
                answer.setNeedsManualGrade(false);
            }
            answerDao.saveOrUpdate(answer);
        }
        return anySubjective;
    }

    private double getQuestionScore(int examId, int questionId) {
        List<ExamQuestion> eqs = examQuestionDao.findByExamId(examId);
        for (ExamQuestion eq : eqs) {
            if (eq.getQuestionId() == questionId) return eq.getScore();
        }
        return 10.0;
    }

    private boolean compareObjectiveAnswer(String student, String correct, QuestionTypeEnum type) {
        if (student == null || correct == null) return false;
        String s = student.trim().replaceAll("\\s+", "");
        String c = correct.trim().replaceAll("\\s+", "");
        if (s.isEmpty()) return false;

        if (type == QuestionTypeEnum.TRUE_FALSE) {
            return s.equalsIgnoreCase(c) ||
                    (s.equals("对") && c.equals("正确")) ||
                    (s.equals("错") && c.equals("错误")) ||
                    ((s.equals("T") || s.equals("Y") || s.equals("YES")) && c.equals("正确")) ||
                    ((s.equals("F") || s.equals("N") || s.equals("NO")) && c.equals("错误"));
        }
        if (type == QuestionTypeEnum.MULTIPLE_CHOICE) {
            String[] sa = s.toUpperCase().replaceAll("[,，;；\\s]", "").split("");
            String[] ca = c.toUpperCase().replaceAll("[,，;；\\s]", "").split("");
            List<String> listA = new ArrayList<>(Arrays.asList(sa));
            List<String> listB = new ArrayList<>(Arrays.asList(ca));
            listA.removeIf(x -> x == null || x.isEmpty());
            listB.removeIf(x -> x == null || x.isEmpty());
            Collections.sort(listA);
            Collections.sort(listB);
            return listA.equals(listB);
        }
        return s.equalsIgnoreCase(c);
    }

    public Result finalizeResult(int examId, int studentId, int sessionId, double totalScore, boolean hasSubjective) {
        double obtained = 0;
        List<Answer> answers = answerDao.findBySessionId(sessionId);
        for (Answer a : answers) {
            obtained += a.getEffectiveScore();
        }
        Result r = new Result();
        r.setExamId(examId);
        r.setStudentId(studentId);
        r.setScore(obtained);
        r.setTotalScore(totalScore);
        r.setAiAnalysis(hasSubjective ? "存在主观题，请教师人工评分" : "客观题已自动批改完成");
        int rid = resultDao.create(r);
        r.setId(rid);
        return r;
    }

    public double recalcResultScore(int resultId, int sessionId) {
        List<Answer> answers = answerDao.findBySessionId(sessionId);
        double score = 0;
        for (Answer a : answers) {
            score += a.getEffectiveScore();
        }
        resultDao.updateScore(resultId, score);
        return score;
    }

    // ================== AI 辅助评阅 ==================
    public Map<String, Object> aiGradeAnswer(int answerId) {
        Optional<Answer> opt = answerDao.findById(answerId);
        if (!opt.isPresent()) return null;
        Answer a = opt.get();

        // 查题目信息
        Question q = new Question();
        q.setContent(a.getQuestionContent());
        q.setAnswer(a.getCorrectAnswer());
        q.setType(a.getQuestionType() == null ? QuestionTypeEnum.SHORT_ANSWER
                : QuestionTypeEnum.fromValue(a.getQuestionType()));

        double maxScore = a.getQuestionScore() > 0 ? a.getQuestionScore() : 10.0;

        Map<String, Object> aiResult = client.gradeAnswer(
                q.getContent(), q.getAnswer(), a.getStudentAnswer(),
                q.getType() == null ? "short_answer" : q.getType().getValue(),
                maxScore,
                "medium");

        // 写入 Answer 的 AI 字段
        double aiScore = aiResult.get("score") instanceof Number ? ((Number) aiResult.get("score")).doubleValue() : 0;
        String analysis = (String) aiResult.get("analysis");
        answerDao.updateAI(answerId, aiScore, analysis);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answerId", answerId);
        out.put("aiScore", aiScore);
        out.put("isCorrect", aiResult.get("is_correct"));
        out.put("aiAnalysis", analysis);
        out.put("maxScore", maxScore);
        return out;
    }

    // ================== AI 考试分析 ==================
    public Map<String, Object> aiAnalyzeResult(int resultId) {
        Optional<Result> opt = resultDao.findById(resultId);
        if (!opt.isPresent()) return null;
        Result r = opt.get();

        String studentName = resultDao.findStudentName(r.getStudentId());
        if (studentName == null) studentName = "学生";

        Map<String, Object> out = client.analyzeExamResult(
                r.getExamTitle() != null ? r.getExamTitle() : "考试 #" + r.getExamId(),
                studentName,
                r.getScore(),
                r.getTotalScore(),
                1,
                0,
                new ArrayList<>());

        // 写回数据库
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(String.valueOf(out.get("level")));
        sb.append("] ");
        sb.append(String.valueOf(out.get("summary")));
        resultDao.updateAIAnalysis(r.getId(), sb.toString());

        return out;
    }

    // ================== AI 推荐 ==================
    public Map<String, Object> aiRecommend(String studentName, List<String> weakTopics, int count) {
        return client.recommendQuestions(weakTopics, studentName, count);
    }

    // ================== AI 批量出题 ==================
    public List<Map<String, Object>> aiGenerateBatch(String major, int count, String difficulty) {
        return client.generateBatch(major, count, difficulty);
    }
}