package com.exam.dto;

import lombok.Data;

/**
 * 「每个题库有多少可用题目」的统计行（练习页的题库下拉用）。
 *
 * <p>为什么用强类型而不是 {@code Map}：H2 会把列标签转成大写，
 * {@code map.get("total")} 会静默拿到 null。别名用下划线风格由 MyBatis 的
 * 「下划线转驼峰」映射到这个类上，两个引擎行为一致。</p>
 */
@Data
public class BankQuestionCount {

    private Long bankId;
    private Integer total;
}