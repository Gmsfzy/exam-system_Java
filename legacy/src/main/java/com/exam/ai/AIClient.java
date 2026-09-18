package com.exam.ai;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AIClient {
    private final boolean online;
    private final String apiKey;
    private final String modelName;
    private final Random random = new Random();

    public AIClient() {
        // ====== 1. 优先读取 config.properties 文件（类似 .env）======
        String keyFromFile = null;
        String modelFromFile = "doubao-seed-1-6-250615";
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.File f = new java.io.File("config.properties");
            if (f.exists()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    props.load(fis);
                }
                keyFromFile = props.getProperty("DOUBAN_API_KEY");
                if (keyFromFile != null && keyFromFile.trim().isEmpty()) keyFromFile = null;
                String m = props.getProperty("AI_MODEL");
                if (m != null && !m.trim().isEmpty()) modelFromFile = m.trim();
            }
        } catch (Exception ignored) {}

        // ====== 2. 文件中没有，则读系统环境变量（如 Python 的 os.getenv）======
        if (keyFromFile == null) {
            keyFromFile = System.getenv("DOUBAN_API_KEY");
        }
        // ====== 3. 再兜底读系统属性（java -DDOUBAN_API_KEY=xxx）======
        if (keyFromFile == null) {
            keyFromFile = System.getProperty("DOUBAN_API_KEY");
        }

        this.apiKey = keyFromFile;
        this.modelName = modelFromFile;
        this.online = apiKey != null && !apiKey.isEmpty();
        System.out.println("[AIClient] online mode = " + online + " (model=" + modelName + ", key from: "
                + (keyFromFile == null ? "NONE" : "config.properties/env") + ")");
    }

    public Map<String, Object> generateQuestion(String major, String type, String difficulty) {
        return generateQuestion(major, type, difficulty, "", "");
    }

    public Map<String, Object> generateQuestion(String major, String type, String difficulty,
                                                String keywords, String hint) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (online) {
            try {
                return callDoubao(major, type, difficulty, keywords, hint);
            } catch (Exception e) {
                System.err.println("[AIClient] 在线调用失败，退回离线模式: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return generateOffline(major, type, difficulty, keywords, hint);
    }

    private Map<String, Object> callDoubao(String major, String type, String difficulty) throws Exception {
        return callDoubao(major, type, difficulty, "", "");
    }

    private Map<String, Object> callDoubao(String major, String type, String difficulty,
                                          String keywords, String hint) throws Exception {
        String typeLabel = typeLabel(type);
        String diffLabel = diffLabel(difficulty);

        String systemPrompt = "你是一名专业的高校命题教师，擅长出" + major + "学科的题目。"
                + "你必须严格以 JSON 格式返回结果，不要返回任何额外的说明文字、Markdown 标记或代码块标记。"
                + "JSON 只能包含以下字段：content（题目内容）、options（选择题选项数组，非选择题传空数组）、"
                + "answer（参考答案，客观题用字母如 A 或 A,C,D，主观题用文字解析）、"
                + "analysis（题目解析和知识点说明，约50-80字）。"
                + "客观题（单选/多选/判断）的 options 必须有4个字符串元素，单选 answer 是单个字母，多选题 answer 是多个大写字母用英文逗号分隔（如 A,C）。"
                + "判断题 answer 只能是 '正确' 或 '错误'，options 为空数组。"
                + "填空题、简答题、编程题、计算题、应用题的 options 为空数组，answer 给出完整参考答案。"
                + "编程语言题必须写出完整可运行的代码示例作为 answer。"
                + "题目要符合高校本科/研究生难度，内容要严谨、专业，避免出过于简单或有争议的题。";

        String userPrompt = "请出一道【" + major + "】学科的【" + typeLabel + "】，难度：" + diffLabel + "。\n";
        if (keywords != null && !keywords.trim().isEmpty()) {
            userPrompt += "出题关键词：" + keywords + "（请围绕这些关键词设计题目）\n";
        }
        if (hint != null && !hint.trim().isEmpty()) {
            userPrompt += "附加要求：" + hint + "\n";
        }
        userPrompt += "要求：\n"
                + "1. 题目内容要具体、有考点，不能太泛\n"
                + "2. 选择题的四个选项要有明显的区分度，正确答案要隐藏得当\n"
                + "3. 解析要说明所考察的知识点、易错点\n"
                + "4. 严格按 JSON 格式返回，不要任何额外文字";

        // 调用豆包 API（OpenAI 兼容格式）
        URL url = new URL("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        String body = "{\"model\":\"" + modelName + "\",\"messages\":["
                        + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                        + "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                        + "],\"temperature\":0.8,\"max_tokens\":1200}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            String err = readStream(conn.getErrorStream());
            throw new RuntimeException("AI API 错误 " + code + ": " + err);
        }

        String resp = readStream(conn.getInputStream());
        System.out.println("[AIClient] 豆包返回长度: " + resp.length());

        // 解析返回 JSON，提取 AI 的内容
        // 豆包返回格式: {"choices":[{"message":{"content":"{json字符串}"}}]}
        String content = extractJsonContent(resp);
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("AI 返回内容为空");
        }

        // AI 返回的 content 本身应该是一个 JSON 字符串
        content = cleanMarkdownJson(content);

        return parseAIJson(content, type, difficulty, major);
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String extractJsonContent(String rawResp) {
        // 简单解析：查找 "content":" ... " 或 "content": ... 直到下一层结束
        // 更稳健的做法是用完整 JSON 解析库，这里用字符串方式处理
        try {
            int idx = rawResp.indexOf("\"content\"");
            if (idx < 0) return null;
            int colonIdx = rawResp.indexOf(":", idx);
            int startQuote = rawResp.indexOf("\"", colonIdx + 1);
            if (startQuote < 0) return null;

            StringBuilder sb = new StringBuilder();
            int i = startQuote + 1;
            boolean inEscape = false;
            while (i < rawResp.length()) {
                char c = rawResp.charAt(i);
                if (inEscape) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 't') sb.append('\t');
                    else if (c == 'r') sb.append('\r');
                    else if (c == '"') sb.append('"');
                    else if (c == '\\') sb.append('\\');
                    else sb.append(c);
                    inEscape = false;
                    i++;
                    continue;
                }
                if (c == '\\') { inEscape = true; i++; continue; }
                if (c == '"') break; // content 结束
                sb.append(c);
                i++;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String cleanMarkdownJson(String content) {
        // 如果 AI 用 ```json ... ``` 包裹，去掉
        content = content.trim();
        if (content.startsWith("```")) {
            int end = content.indexOf("```", 3);
            if (end > 0) content = content.substring(3, end);
        }
        // 去掉可能的 "json" 前缀
        if (content.toLowerCase().startsWith("json")) content = content.substring(4).trim();
        return content.trim();
    }

    private Map<String, Object> parseAIJson(String aiJson, String type, String difficulty, String major) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 手动解析简单字段（避免引入额外依赖）
            String contentField = extractField(aiJson, "content");
            String answerField = extractField(aiJson, "answer");
            String analysisField = extractField(aiJson, "analysis");
            List<String> optionsField = extractArrayField(aiJson, "options");

            result.put("content", (contentField != null && !contentField.isEmpty()) ? contentField
                    : "[AI-" + major + "-" + type + "] （AI未返回题目）");
            result.put("options", (optionsField != null) ? optionsField : new ArrayList<String>());
            result.put("answer", (answerField != null && !answerField.isEmpty()) ? answerField : "（AI未返回答案）");
            result.put("analysis", (analysisField != null && !analysisField.isEmpty()) ? analysisField
                    : "AI生成的题目解析与知识点说明。");
        } catch (Exception e) {
            System.err.println("[AIClient] JSON 解析失败: " + e.getMessage());
            System.err.println("[AIClient] AI 原始返回: " + aiJson);
            // 解析失败也回退到离线
            return generateOffline(major, type, difficulty, "", "");
        }
        return result;
    }

    // 从 JSON 字符串中提取字段值（支持带转义的字符串）
    private String extractField(String json, String key) {
        String marker = "\"" + key + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx + marker.length());
        if (colon < 0) return null;
        // 跳过空白
        int i = colon + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\t' || json.charAt(i) == '\n' || json.charAt(i) == '\r')) i++;

        if (i >= json.length()) return null;

        // 如果下一个字符是 "，说明是字符串
        if (json.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            boolean inEscape = false;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (inEscape) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 't') sb.append('\t');
                    else if (c == 'r') sb.append('\r');
                    else if (c == '"') sb.append('"');
                    else sb.append(c);
                    inEscape = false;
                    i++;
                    continue;
                }
                if (c == '\\') { inEscape = true; i++; continue; }
                if (c == '"') return sb.toString().trim();
                sb.append(c);
                i++;
            }
        }
        // 非字符串：简单值
        int end = json.indexOf(",", i);
        int endBrace = json.indexOf("}", i);
        int stop = (end > 0 && (endBrace < 0 || end < endBrace)) ? end : endBrace;
        if (stop < 0) stop = json.length();
        return json.substring(i, stop).trim();
    }

    // 从 JSON 中提取字符串数组
    private List<String> extractArrayField(String json, String key) {
        String marker = "\"" + key + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) return new ArrayList<>();
        int colon = json.indexOf(":", idx + marker.length());
        if (colon < 0) return new ArrayList<>();
        int bracketStart = json.indexOf("[", colon);
        if (bracketStart < 0) return new ArrayList<>();
        int depth = 0;
        int i = bracketStart;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) break; }
            i++;
        }
        String arrStr = json.substring(bracketStart + 1, i);

        // 解析字符串数组 ["A","B","C"]
        List<String> result = new ArrayList<>();
        int j = 0;
        while (j < arrStr.length()) {
            while (j < arrStr.length() && (arrStr.charAt(j) == ' ' || arrStr.charAt(j) == ',' || arrStr.charAt(j) == '\n' || arrStr.charAt(j) == '\r' || arrStr.charAt(j) == '\t')) j++;
            if (j >= arrStr.length()) break;
            if (arrStr.charAt(j) == '"') {
                StringBuilder sb = new StringBuilder();
                j++;
                boolean inEscape = false;
                while (j < arrStr.length()) {
                    char c = arrStr.charAt(j);
                    if (inEscape) {
                        if (c == 'n') sb.append('\n');
                        else if (c == 't') sb.append('\t');
                        else if (c == 'r') sb.append('\r');
                        else if (c == '"') sb.append('"');
                        else if (c == '\\') sb.append('\\');
                        else sb.append(c);
                        inEscape = false;
                        j++;
                        continue;
                    }
                    if (c == '\\') { inEscape = true; j++; continue; }
                    if (c == '"') break;
                    sb.append(c);
                    j++;
                }
                j++; // 跳过闭合 "
                result.add(sb.toString());
            } else {
                // 跳过非字符串直到下一个 ,
                while (j < arrStr.length() && arrStr.charAt(j) != ',') j++;
            }
        }
        return result;
    }

    private String typeLabel(String t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("single_choice", "单选题");
        m.put("multiple_choice", "多选题");
        m.put("true_false", "判断题");
        m.put("fill_blank", "填空题");
        m.put("short_answer", "简答题");
        m.put("programming", "编程题");
        m.put("calculation", "计算题");
        m.put("application", "应用题");
        return m.getOrDefault(t, "简答题");
    }

    private String diffLabel(String d) {
        if ("easy".equals(d)) return "简单";
        if ("hard".equals(d)) return "困难";
        return "中等";
    }

    // ================== 离线模板（兜底方案） ==================
    private Map<String, Object> generateOffline(String major, String type, String difficulty,
                                              String keywords, String hint) {
        Map<String, Object> result = new LinkedHashMap<>();
        String kw = (keywords != null && !keywords.trim().isEmpty()) ? keywords : "核心知识点";
        String ht = (hint != null && !hint.trim().isEmpty()) ? (hint + "。") : "";

        if ("programming".equals(type)) {
            List<String> samples = Arrays.asList(
                    "实现一个函数，用于反转字符串 input，并返回反转后的结果。",
                    "编写一个排序算法，对整型数组 arr 进行升序排序并返回。",
                    "编写一个程序，计算给定非负整数 n 的阶乘 n!。",
                    "实现一个二分查找函数，在有序数组 arr 中查找目标值 target，返回下标或 -1。",
                    "编写一个函数，用于检测字符串 s 是否为回文字符串。",
                    "编写一个程序，输出斐波那契数列的前 n 项。",
                    "实现一个链表反转函数，将给定的单链表反转后返回。",
                    "设计一个栈结构，支持 push、pop 和获取最小值 getMin 操作。",
                    "实现二叉树的前序、中序、后序遍历，并分析时间空间复杂度。",
                    "编写动态规划算法，求解最长公共子序列 LCS 的长度。"
            );
            result.put("content", String.format("[AI-%s-编程题-%s] 【考查关键词：%s】%s %s",
                    major, difficulty, kw, ht, samples.get(random.nextInt(samples.size()))));
            result.put("options", new ArrayList<String>());
            result.put("answer", "// 参考实现（伪代码）\n"
                    + "// 1. 明确输入与输出\n"
                    + "// 2. 选择合适的算法与数据结构\n"
                    + "// 3. 设计边界与异常处理\n"
                    + "// 4. 给出时间复杂度与空间复杂度分析\n"
                    + "\nfunction solve(input) {\n  return result;\n}");
            result.put("analysis", String.format(
                    "%s%s编程题考察算法设计能力、代码实现能力及复杂度分析能力，考查关键词：%s。建议关注边界输入（空、单元素、负数、大数等）。",
                    ht, difficulty, kw));
            return result;
        }

        if ("calculation".equals(type)) {
            Map<String, List<String>> samples = new LinkedHashMap<>();
            samples.put("数学", Arrays.asList(
                    "求极限 lim(x→0) sin(x)/x，并给出解题步骤。",
                    "计算定积分 ∫(0 到 1) x² dx 的值。",
                    "求函数 f(x) = x³ - 3x² + 2x 的极值点与拐点。",
                    "已知矩阵 A = [[1,2],[3,4]]，求其逆矩阵 A⁻¹。",
                    "求微分方程 dy/dx + y = x 的通解。",
                    "计算二重积分 ∬(x²+y²) dxdy，其中积分区域为单位圆。",
                    "求级数 Σ(n=1 to ∞) 1/n² 的和，并说明收敛性。"
            ));
            samples.put("物理", Arrays.asList(
                    "一物体从高度 h 自由下落，求落地速度与空气阻力存在时的修正解。",
                    "已知电荷量 q 的点电荷位于坐标原点，求空间任一点的电场强度。",
                    "理想气体从状态 (p₁,V₁,T₁) 等温膨胀到 (p₂,V₂,T₁)，求气体对外做的功。",
                    "应用基尔霍夫定律计算给定直流电路中各支路的电流。"
            ));
            samples.put("计算机科学", Arrays.asList(
                    "已知算法 A 的时间复杂度为 O(n log n)，当 n=1000 时运行时间为 10ms，估计 n=1000000 时的运行时间。",
                    "一台 32 位计算机的主存按字节寻址，若指令寄存器为 32 位，则最多能表示多少种指令？",
                    "计算 1000 阶乘末尾有多少个连续的零，并给出算法思路。"
            ));
            samples.put("默认", Arrays.asList(
                    "结合本课程的主要公式，完成一道综合性的计算题，要求给出完整解题步骤。"
            ));
            List<String> chosen = samples.getOrDefault(major, samples.get("默认"));
            result.put("content", String.format("[AI-%s-计算题-%s] 【考查关键词：%s】%s %s",
                    major, difficulty, kw, ht, chosen.get(random.nextInt(chosen.size()))));
            result.put("options", new ArrayList<String>());
            result.put("answer", "参考答案：\n[解题步骤]\n1. 分析已知条件，列出相关公式\n2. 代入数值进行单位换算\n3. 列出关键方程并求解\n4. 检验结果合理性并给出物理/数学意义\n\n[最终答案] 请根据题目具体条件进行计算。");
            result.put("analysis", String.format(
                    "%s%s计算题主要考查对核心公式（%s）的理解与应用能力，以及严谨的解题逻辑。建议解题时先写公式，再代入数值，最后进行单位与合理性检查。",
                    ht, difficulty, kw));
            return result;
        }

        if ("application".equals(type)) {
            Map<String, List<String>> samples = new LinkedHashMap<>();
            samples.put("计算机科学", Arrays.asList(
                    "某高校需要建设学生选课系统，请从需求分析、数据库设计、前后端架构三个方面给出设计方案，并说明关键技术选型。",
                    "结合实际场景，分析为什么在高并发 Web 系统中需要使用缓存与消息队列，并给出典型应用架构图。",
                    "某图书馆需要实现图书推荐功能，请从数据特征、算法选型与评价指标三个方面给出设计思路。",
                    "请设计一个日志分析系统的架构，要求能够处理每日 1000 万条日志数据的采集、存储、检索。"
            ));
            samples.put("数学", Arrays.asList(
                    "某城市轨道交通系统需要建立客流预测模型，请说明如何运用统计学与机器学习方法进行建模。",
                    "结合线性代数知识，说明如何利用矩阵进行图像压缩与恢复，并分析压缩率与失真度的关系。"
            ));
            samples.put("物理", Arrays.asList(
                    "一栋高层建筑需要设计其电梯运行策略，请从运动学角度分析最优加减速曲线以提高舒适度与效率。",
                    "结合热力学第二定律，分析提高内燃机效率的技术手段及其现实限制。"
            ));
            samples.put("默认", Arrays.asList(
                    "结合本专业实际，选择一个典型问题，要求完成：①问题背景描述；②建立模型或理论框架；③提出解决方案或算法步骤；④给出一个简单的数值或案例验证。",
                    "任选本课程中的一个核心理论，将其应用到一个实际工程或生活场景中，给出完整的应用设计方案。"
            ));
            List<String> chosen = samples.getOrDefault(major, samples.get("默认"));
            result.put("content", String.format("[AI-%s-应用题-%s] 【考查关键词：%s】%s %s",
                    major, difficulty, kw, ht, chosen.get(random.nextInt(chosen.size()))));
            result.put("options", new ArrayList<String>());
            result.put("answer", "参考解答框架：\n[第一步] 问题背景与需求分析\n  - 明确目标与约束条件\n  - 列出相关的假设与已知数据\n\n[第二步] 建立模型/设计方案\n  - 运用本课程核心理论建立模型\n  - 明确关键变量与边界条件\n\n[第三步] 求解与实现\n  - 给出详细求解过程\n  - 说明实现要点与关键技术\n\n[第四步] 验证与讨论\n  - 代入数值案例或进行案例验证\n  - 分析结果合理性、局限性、改进方向");
            result.put("analysis", String.format(
                    "%s%s应用题着重考察学生将理论知识（%s）迁移到实际场景的能力，要求具备问题抽象、方案设计、综合分析的能力。",
                    ht, difficulty, kw));
            return result;
        }

        // 通用题型
        result.put("content", String.format("[AI-%s-%s] 【考查关键词：%s】%s这是一道%s难度的%s题目示例，请根据知识点作答。",
                major, type, kw, ht, difficulty, type));
        if ("single_choice".equals(type) || "multiple_choice".equals(type)) {
            result.put("options", Arrays.asList("选项A内容", "选项B内容", "选项C内容", "选项D内容"));
            result.put("answer", "single_choice".equals(type) ? "A" : "A,C");
        } else if ("true_false".equals(type)) {
            result.put("options", new ArrayList<String>());
            result.put("answer", random.nextBoolean() ? "正确" : "错误");
        } else {
            result.put("options", new ArrayList<String>());
            result.put("answer", "这是AI生成的参考答案示例");
        }
        result.put("analysis", String.format("AI 生成的题目解析，考查关键词：%s。%s核心知识点理解与应用能力。",
                kw, ht));
        return result;
    }

    // ================== AI 阅卷 ==================
    public Map<String, Object> gradeAnswer(String questionContent, String correctAnswer, String studentAnswer,
                                            String questionType, double maxScore, String difficulty) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (studentAnswer == null || studentAnswer.trim().isEmpty()) {
            result.put("score", 0.0);
            result.put("is_correct", false);
            result.put("analysis", "未作答，得 0 分。");
            return result;
        }

        if (online) {
            try {
                String typeLabel = typeLabel(questionType);
                String systemPrompt = "你是一名高校教师，负责批改学生的主观题答案，必须严格、专业、公正。"
                        + "返回格式：{\"score\":数字0到" + maxScore + ",\"is_correct\":布尔值,\"analysis\":\"评分理由和反馈建议\"}。";
                String userPrompt = "题目（" + typeLabel + "，满分" + maxScore + "分，难度:" + diffLabel(difficulty) + "）：\n"
                        + questionContent
                        + "\n\n参考答案：\n" + (correctAnswer == null ? "(无)" : correctAnswer)
                        + "\n\n学生答案：\n" + studentAnswer
                        + "\n\n请评分并给出简短点评。";

                URL url = new URL("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);

                String body = "{\"model\":\"" + modelName + "\",\"messages\":["
                        + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                        + "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                        + "],\"temperature\":0.3,\"max_tokens\":500}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    String resp = readStream(conn.getInputStream());
                    String content = extractJsonContent(resp);
                    if (content != null) {
                        content = cleanMarkdownJson(content);
                        double score = 0;
                        boolean isCorrect = false;
                        String analysis = null;
                        try {
                            String s = extractField(content, "score");
                            if (s != null) score = Double.parseDouble(s.trim());
                        } catch (Exception ignored) {}
                        try {
                            String s = extractField(content, "is_correct");
                            if (s != null) isCorrect = Boolean.parseBoolean(s.trim());
                        } catch (Exception ignored) {}
                        analysis = extractField(content, "analysis");

                        score = Math.max(0, Math.min(maxScore, score));
                        if (!isCorrect && score > maxScore * 0.6) isCorrect = score >= maxScore * 0.7;

                        result.put("score", score);
                        result.put("is_correct", isCorrect);
                        result.put("analysis", analysis != null ? analysis : "AI 评阅完成。");
                        return result;
                    }
                }
            } catch (Exception e) {
                System.err.println("[AIClient] 在线阅卷失败: " + e.getMessage());
            }
        }

        return gradeOffline(questionContent, correctAnswer, studentAnswer, maxScore);
    }

    private Map<String, Object> gradeOffline(String questionContent, String correctAnswer,
                                             String studentAnswer, double maxScore) {
        Map<String, Object> result = new LinkedHashMap<>();
        String sa = (studentAnswer == null) ? "" : studentAnswer.trim();
        String ca = (correctAnswer == null) ? "" : correctAnswer.trim();

        if (sa.isEmpty()) {
            result.put("score", 0.0);
            result.put("is_correct", false);
            result.put("analysis", "未作答，得 0 分。");
            return result;
        }

        java.util.Set<String> keywords = new java.util.LinkedHashSet<>();
        if (!ca.isEmpty()) {
            for (String t : ca.replaceAll("[，,。.;；:：、!！?？\"“”'\\(\\)（）\\[\\]【】]", " ")
                    .replaceAll("\\s+", " ").split(" ")) {
                if (t.length() >= 2) keywords.add(t.toLowerCase());
            }
        }

        int hit = 0;
        int total = Math.max(keywords.size(), 1);
        for (String kw : keywords) {
            if (sa.toLowerCase().contains(kw)) hit++;
        }

        double lengthFactor = Math.min(1.0, sa.length() / 100.0);
        double keywordFactor = (double) hit / total;
        double score = Math.min(maxScore, Math.round((0.55 * keywordFactor + 0.35 * lengthFactor + 0.1) * maxScore * 10) / 10.0);

        StringBuilder analysis = new StringBuilder();
        analysis.append("[离线评阅] ");
        if (hit == 0 && keywords.size() > 2) {
            analysis.append("答案中未命中参考答案的核心知识点，建议补充相关内容。");
            score = Math.min(score, maxScore * 0.3);
        } else if (keywordFactor >= 0.6) {
            analysis.append("命中大部分关键词，与参考答案思路一致，论述较完整。");
        } else if (keywordFactor >= 0.3) {
            analysis.append("命中部分关键词，对知识点有基本掌握但论述不够全面。");
        } else {
            analysis.append("关键词命中率较低，需要重新审题并展开论述。");
        }
        analysis.append(" (关键词命中 ").append(hit).append("/").append(keywords.size()).append(")");

        result.put("score", score);
        result.put("is_correct", score >= maxScore * 0.6);
        result.put("analysis", analysis.toString());
        return result;
    }

    public Map<String, Object> analyzeExamResult(String examTitle, String studentName,
                                                  double score, double totalScore,
                                                  int totalQuestions, int correctCount,
                                                  java.util.List<Map<String, Object>> wrongQuestions) {
        Map<String, Object> result = new LinkedHashMap<>();
        double pct = totalScore > 0 ? score / totalScore : 0;
        String level = pct >= 0.85 ? "优秀" : pct >= 0.7 ? "良好" : pct >= 0.6 ? "及格" : "不及格";

        if (online) {
            try {
                StringBuilder wrongSummary = new StringBuilder();
                if (wrongQuestions != null && !wrongQuestions.isEmpty()) {
                    wrongSummary.append("答错/失分题详情：\n");
                    int i = 1;
                    for (Map<String, Object> w : wrongQuestions) {
                        wrongSummary.append(i).append(". [").append(w.get("type")).append("] ");
                        String qc = (String) w.get("content");
                        if (qc != null && qc.length() > 80) qc = qc.substring(0, 80) + "...";
                        wrongSummary.append(qc).append("\n");
                        wrongSummary.append("   学生答案: ").append(w.get("studentAnswer")).append("\n");
                        wrongSummary.append("   参考答案: ").append(w.get("correctAnswer")).append("\n");
                        i++;
                        if (i > 8) { wrongSummary.append("...共").append(wrongQuestions.size()).append("题\n"); break; }
                    }
                }

                String systemPrompt = "你是一名专业的高校学习分析师。你必须严格以 JSON 返回："
                        + "{\"level\":\"评价等级\", \"strength\":\"优点分析50字\", \"weakness\":\"薄弱点分析80字\", \"knowledge\":\"涉及的知识点列表\", \"advice\":\"具体学习建议100字\", \"summary\":\"一句话总结\"}。";
                String userPrompt = "考试：" + examTitle + "\n学生：" + studentName
                        + "\n成绩：" + score + " / " + totalScore + " (" + Math.round(pct * 100) + "%)"
                        + "\n共" + totalQuestions + "题，正确" + correctCount + "题。\n"
                        + (wrongSummary.length() > 0 ? wrongSummary.toString() : "");

                URL url = new URL("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(90000);
                conn.setDoOutput(true);

                String body = "{\"model\":\"" + modelName + "\",\"messages\":["
                        + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                        + "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                        + "],\"temperature\":0.6,\"max_tokens\":800}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    String resp = readStream(conn.getInputStream());
                    String content = extractJsonContent(resp);
                    if (content != null) {
                        content = cleanMarkdownJson(content);
                        result.put("level", safeField(content, "level", level));
                        result.put("strength", safeField(content, "strength", "答题表现基本稳定。"));
                        result.put("weakness", safeField(content, "weakness", "部分题目存在审题或知识点掌握不足。"));
                        result.put("knowledge", safeField(content, "knowledge", "考试涉及的知识点"));
                        result.put("advice", safeField(content, "advice", "建议复习错题，针对性强化薄弱知识点。"));
                        result.put("summary", safeField(content, "summary", "本次考试整体表现" + level + "。"));
                        return result;
                    }
                }
            } catch (Exception e) {
                System.err.println("[AIClient] 在线分析失败: " + e.getMessage());
            }
        }

        result.put("level", level);
        result.put("strength", pct >= 0.6
                ? "客观题与部分主观题答题正确率较高，基础知识掌握较扎实。"
                : "虽然整体分数偏低，但从作答情况看具备一定基础。");
        result.put("weakness", pct < 0.6
                ? "失分点主要集中在：主观题思路不清晰、审题不够仔细、知识点记忆零散、答题规范性不足。"
                : "错题主要集中在综合应用或较为复杂的题型，说明对知识点的迁移应用能力有待加强。");
        result.put("knowledge", "本次考试覆盖全部课程核心知识点（详见题目列表）");
        result.put("advice", "建议：① 逐题分析错题原因（审题失误/知识点遗漏/思路错误）并做标记；② 针对错题对应的知识点进行专题复习；③ 重做同类题型以巩固；④ 主观题作答要先搭框架再展开论述。");
        result.put("summary", studentName + "在本次" + examTitle + "中得" + score + "分（" + Math.round(pct * 100) + "%），评级为" + level + "。");
        return result;
    }

    private String safeField(String json, String key, String defaultValue) {
        String v = extractField(json, key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    public Map<String, Object> recommendQuestions(java.util.List<String> weakTopics, String studentName,
                                                   int needCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> items = new ArrayList<>();
        if (weakTopics != null) {
            for (int i = 0; i < Math.min(needCount, weakTopics.size() + 3); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                String topic = i < weakTopics.size() ? weakTopics.get(i) : "综合知识点";
                item.put("title", "练习：" + topic + " 相关综合题");
                item.put("reason", "针对" + studentName + "的薄弱环节，该练习有助于加深对" + topic + "的理解。");
                item.put("difficulty", i < weakTopics.size() / 2 ? "中等" : "困难");
                item.put("topic", topic);
                items.add(item);
            }
        }
        result.put("items", items);
        result.put("count", items.size());
        result.put("summary", "共推荐 " + items.size() + " 道练习题，建议按顺序完成，每题完成后对照参考答案进行自评分。");
        return result;
    }

    public java.util.List<Map<String, Object>> generateBatch(String major, int count, String difficulty) {
        java.util.List<Map<String, Object>> list = new ArrayList<>();
        java.util.List<String> types = java.util.Arrays.asList(
                "single_choice", "multiple_choice", "true_false",
                "fill_blank", "short_answer", "programming",
                "calculation", "application");
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < count; i++) {
            String type = types.get(rnd.nextInt(types.size()));
            list.add(generateQuestion(major, type, difficulty));
        }
        return list;
    }
}