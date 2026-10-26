package com.exam.dto;

import lombok.Data;

/**
 * 考试成绩聚合结果。
 *
 * <p>刻意用<b>强类型 DTO</b> 而不是 {@code Map<String, Object>}：Map 的 key 直接来自
 * JDBC 的列标签，而列标签大小写由数据库与连接参数决定（H2 的
 * {@code DATABASE_TO_LOWER=TRUE} 会把别名统一转成小写），
 * 于是同一个 SQL 在 MySQL 上取 {@code submittedCount}、
 * 在 H2 上得取 {@code submittedcount}——这种依赖一旦成立，
 * 测试通过而生产报错（或反之）几乎无法排查。
 * 换成 DTO 后由 MyBatis 按「下划线转驼峰 + 忽略大小写」匹配属性，跨库稳定。</p>
 */
@Data
public class ExamRecordStats {

    /** 参加考试的总人数（含未交卷） */
    private Integer totalCount;

    /** 已交卷人数 */
    private Integer submittedCount;

    /** 平均分 */
    private Double avgScore;

    /** 最高分 */
    private Integer maxScore;

    /** 最低分 */
    private Integer minScore;
}