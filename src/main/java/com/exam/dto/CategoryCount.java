package com.exam.dto;

import lombok.Data;

/**
 * 「每个知识点有多少道可用题目」的统计行（V4）。
 *
 * <p>题库管理的专题筛选、智能组卷的规则、自由练习的专题下拉都用它 ——
 * 刻意返回「库里实际存在的知识点」而不是一张写死的词表：
 * 标签是老师自己填的，写死的词表很快会和库里的实际值对不上，
 * 于是下拉里选到一个搜不到东西的选项（那种「筛选没反应」最难解释）。</p>
 *
 * <p>与 {@link BankQuestionCount} 同样的理由用强类型而不是 {@code Map}：
 * H2 会把列标签转成大写，{@code map.get("total")} 会静默拿到 null。</p>
 */
@Data
public class CategoryCount {

    private String category;
    private Integer total;
}