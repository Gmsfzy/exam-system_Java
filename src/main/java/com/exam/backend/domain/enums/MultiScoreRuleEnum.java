package com.exam.backend.domain.enums;

/** 多选题判分规则(M6):all_or_nothing=全对才得分,partial=漏选按比例得分、含错选 0 分 */
public enum MultiScoreRuleEnum {
    all_or_nothing,
    partial
}
