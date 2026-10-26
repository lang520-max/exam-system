package com.exam.dto.response;

import lombok.Data;

/**
 * 练习页的题库选项：题库基本信息 + 可用题目数。
 *
 * <p>为什么不复用 {@code /api/banks}：那个接口是 ADMIN/TEACHER 专属的（题库是教学资产），
 * 而练习页是学生用的。这里只暴露「练哪个库」需要的最小信息：id / 名称 / 科目 / 题量。</p>
 */
@Data
public class PracticeBankVO {

    private Long id;
    private String name;
    private String subject;
    /** 该题库启用中的题目数（为 0 的库不返回，免得学生点进去是空的） */
    private Integer total;
}