package com.exam.backend.util;

import com.exam.backend.domain.enums.QuestionTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerComparatorTest {

    private AnswerComparator comparator;

    @BeforeEach
    void setUp() {
        comparator = new AnswerComparator();
    }

    @Test
    @DisplayName("单选：大小写不敏感")
    void singleChoiceCaseInsensitive() {
        assertThat(comparator.isCorrect(QuestionTypeEnum.single_choice, "a", "A")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.single_choice, "B", "D")).isFalse();
    }

    @Test
    @DisplayName("多选：顺序无关、分隔符兼容（逗号/中文逗号/分号）")
    void multipleChoiceOrderInsensitive() {
        assertThat(comparator.isCorrect(QuestionTypeEnum.multiple_choice, "A,B", "b,a")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.multiple_choice, "A，B；C", "c,b,a")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.multiple_choice, "A,B", "A,C")).isFalse();
    }

    @Test
    @DisplayName("判断：中英文布尔归一化")
    void trueFalseNormalized() {
        assertThat(comparator.isCorrect(QuestionTypeEnum.true_false, "对", "正确")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.true_false, "T", "true")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.true_false, "错", "F")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.true_false, "对", "错")).isFalse();
    }

    @Test
    @DisplayName("填空：忽略大小写")
    void fillBlank() {
        assertThat(comparator.isCorrect(QuestionTypeEnum.fill_blank, "LIFO", "lifo")).isTrue();
        assertThat(comparator.isCorrect(QuestionTypeEnum.fill_blank, "栈", "队列")).isFalse();
    }

    @Test
    @DisplayName("主观题不参与自动判分")
    void subjectiveAlwaysFalse() {
        assertThat(comparator.isCorrect(QuestionTypeEnum.short_answer, "x", "x")).isFalse();
        assertThat(comparator.isCorrect(QuestionTypeEnum.programming, "x", "x")).isFalse();
    }

    @Test
    @DisplayName("空答案与 null 判错")
    void nullAndBlank() {
        assertThat(comparator.isCorrect(QuestionTypeEnum.single_choice, null, "A")).isFalse();
        assertThat(comparator.isCorrect(QuestionTypeEnum.single_choice, "A", null)).isFalse();
        assertThat(comparator.isCorrect(QuestionTypeEnum.single_choice, " ", "A")).isFalse();
    }
}
