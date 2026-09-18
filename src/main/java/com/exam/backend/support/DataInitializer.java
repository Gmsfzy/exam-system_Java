package com.exam.backend.support;

import com.exam.backend.domain.entity.*;
import com.exam.backend.domain.enums.*;
import com.exam.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamStudentRepository examStudentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("DataInitializer: 数据库已有用户,跳过种子数据");
            return;
        }
        log.info("DataInitializer: 开始初始化种子数据...");
        log.warn("DataInitializer: 使用默认种子密码,请在生产环境前修改!");

        String seedPassword = "Exam@2026!";
        // 用户
        User teacher = userRepository.save(User.builder()
                .username("teacher")
                .email("teacher@example.com")
                .passwordHash(passwordEncoder.encode(seedPassword))
                .role(RoleEnum.teacher)
                .build());
        User student1 = userRepository.save(User.builder()
                .username("student1")
                .email("student1@example.com")
                .passwordHash(passwordEncoder.encode(seedPassword))
                .role(RoleEnum.student)
                .build());
        userRepository.save(User.builder()
                .username("student2")
                .email("student2@example.com")
                .passwordHash(passwordEncoder.encode(seedPassword))
                .role(RoleEnum.student)
                .build());

        // 院系 / 专业 / 课程 / 章节
        Department dept = departmentRepository.save(Department.builder()
                .name("计算机学院")
                .description("计算机科学与技术相关")
                .build());
        Major major = majorRepository.save(Major.builder()
                .name("计算机科学与技术")
                .description("CS 本科")
                .department(dept)
                .build());
        Course course = courseRepository.save(Course.builder()
                .name("数据结构")
                .description("基础课程")
                .credit(3)
                .semester(2)
                .major(major)
                .build());
        Chapter ch1 = chapterRepository.save(Chapter.builder()
                .name("第一章 线性表")
                .description("线性表基础")
                .orderNum(1)
                .course(course)
                .build());
        Chapter ch2 = chapterRepository.save(Chapter.builder()
                .name("第二章 栈与队列")
                .description("栈和队列")
                .orderNum(2)
                .course(course)
                .build());

        // 8 种题型样例题
        List<Question> samples = List.of(
                Question.builder()
                        .content("下列关于线性表的描述,正确的是?")
                        .options(List.of("A. 顺序表支持随机访问", "B. 链表支持随机访问", "C. 两者都支持", "D. 都不支持"))
                        .answer("A")
                        .analysis("顺序表由于内存连续,支持O(1)随机访问")
                        .knowledge("线性表")
                        .type(QuestionTypeEnum.single_choice)
                        .difficulty(DifficultyEnum.easy)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch1.getId())
                        .build(),
                Question.builder()
                        .content("下列属于线性数据结构的有?")
                        .options(List.of("A. 栈", "B. 队列", "C. 树", "D. 图"))
                        .answer("A,B")
                        .analysis("栈和队列都是线性结构")
                        .knowledge("线性表")
                        .type(QuestionTypeEnum.multiple_choice)
                        .difficulty(DifficultyEnum.medium)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch1.getId())
                        .build(),
                Question.builder()
                        .content("栈的特点是 ___ 出入。")
                        .options(List.of())
                        .answer("后进先出")
                        .analysis("LIFO (Last In First Out)")
                        .knowledge("栈")
                        .type(QuestionTypeEnum.fill_blank)
                        .difficulty(DifficultyEnum.easy)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch2.getId())
                        .build(),
                Question.builder()
                        .content("队列的特点是后进先出。")
                        .options(List.of())
                        .answer("错")
                        .analysis("队列是 FIFO,栈才是 LIFO")
                        .knowledge("队列")
                        .type(QuestionTypeEnum.true_false)
                        .difficulty(DifficultyEnum.easy)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch2.getId())
                        .build(),
                Question.builder()
                        .content("简述顺序表与链表的区别。")
                        .options(List.of())
                        .answer("顺序表内存连续,支持随机访问;链表通过指针链接,只能顺序访问。")
                        .analysis("主要从存储方式、访问方式、插入删除效率三方面比较。")
                        .knowledge("线性表")
                        .type(QuestionTypeEnum.short_answer)
                        .difficulty(DifficultyEnum.medium)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch1.getId())
                        .build(),
                Question.builder()
                        .content("实现一个栈类,要求支持 push/pop/top 操作。")
                        .options(List.of())
                        .answer("class Stack { ... }")
                        .analysis("使用 ArrayList 或数组即可实现")
                        .knowledge("栈")
                        .type(QuestionTypeEnum.programming)
                        .difficulty(DifficultyEnum.hard)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch2.getId())
                        .build(),
                Question.builder()
                        .content("给定一个含 n 个元素的数组,设计算法找出第 k 大的元素。")
                        .options(List.of())
                        .answer("使用快速选择算法,平均 O(n)")
                        .analysis("快速选择基于快排的分区思想")
                        .knowledge("算法")
                        .type(QuestionTypeEnum.application)
                        .difficulty(DifficultyEnum.hard)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch1.getId())
                        .build(),
                Question.builder()
                        .content("求斐波那契数列第 n 项的时间复杂度（递归实现）。")
                        .options(List.of())
                        .answer("O(2^n)")
                        .analysis("递归树呈指数增长")
                        .knowledge("时间复杂度")
                        .type(QuestionTypeEnum.calculation)
                        .difficulty(DifficultyEnum.hard)
                        .creatorId(teacher.getId())
                        .isPublic(true)
                        .majorId(major.getId())
                        .courseId(course.getId())
                        .chapterId(ch1.getId())
                        .build());
        questionRepository.saveAll(samples);

        // 测试考试
        LocalDateTime now = LocalDateTime.now();
        Exam exam = examRepository.save(Exam.builder()
                .title("数据结构期中测试")
                .description("样例考试")
                .startTime(now.plusMinutes(-5))
                .endTime(now.plusDays(1))
                .duration(60)
                .status(ExamStatusEnum.published)
                .creatorId(teacher.getId())
                .invitationCode("TEST1234")
                .invitationUrl("/exam/join/TEST1234")
                .build());

        // 加入前 5 道题
        int order = 1;
        for (Question q : samples.subList(0, 5)) {
            examQuestionRepository.save(ExamQuestion.builder()
                    .examId(exam.getId())
                    .questionId(q.getId())
                    .score(10)
                    .order(order++)
                    .build());
        }

        // 邀请学生 1
        examStudentRepository.save(ExamStudent.builder()
                .examId(exam.getId())
                .studentId(student1.getId())
                .invitedAt(now)
                .build());

        log.info("DataInitializer: 种子数据初始化完成");
        log.info("  - 教师: teacher / 123123");
        log.info("  - 学生1: student1 / 123123  (已邀请参加考试)");
        log.info("  - 学生2: student2 / 123123");
        log.info("  - 考试: 数据结构期中测试 (邀请码 TEST1234)");
        log.info("  - 题库: 8 道样例题 (覆盖全部题型)");
    }
}