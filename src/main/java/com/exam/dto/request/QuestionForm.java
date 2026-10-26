package com.exam.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 题目新增 / 修改参数。
 *
 * <p><b>为什么不再直接绑定 {@code Question} 实体</b>：实体是「库里那一行」的映射，
 * 上面没有任何校验注解，控制器上写的 {@code @Valid} 于是一直空转 ——
 * 一个只带 {@code content} 的请求就能建出 type/score 全为 null 的脏题，
 * 之后判分时这些题既无题型也无分值，只能一路 NPE 或静默丢分。
 * 请求参数与持久化对象分开后，校验才真正生效。</p>
 *
 * <p><b>为什么这里没有 id / createdBy / createdAt / updatedAt</b>：
 * 更新走「按白名单字段赋值」而不是把请求体整个灌进实体，
 * 客户端就没有机会篡改归属与时间戳 —— 否则教师可以把别人的题改成自己的，
 * 或者把 {@code created_at} 改成任意值来绕过基于时间的统计。</p>
 */
@Data
public class QuestionForm {

    /** 所属题库 */
    @NotNull(message = "题库不能为空")
    private Long bankId;

    /** 0-单选 1-多选 2-判断 3-填空 4-简答 */
    @NotNull(message = "题型不能为空")
    @Min(value = 0, message = "题型不合法")
    @Max(value = 4, message = "题型不合法")
    private Integer type;

    /** 1-简单 2-中等 3-困难；留空按中等处理 */
    @Min(value = 1, message = "难度必须是 1-3")
    @Max(value = 3, message = "难度必须是 1-3")
    private Integer difficulty;

    /** 题干 */
    @NotBlank(message = "题干不能为空")
    private String content;

    /** 标准答案 */
    @NotBlank(message = "答案不能为空")
    private String answer;

    /** 分值 */
    @NotNull(message = "分值不能为空")
    @Min(value = 1, message = "分值必须大于 0")
    private Integer score;

    /**
     * 选项，JSON 字符串。选择题必填，判断题与主观题可留空：
     * <pre>
     *   [{"label":"A","content":"选项一"}, ...]
     *   {"A":"选项一","B":"选项二"}
     *   [["A","选项一"],["B","选项二"]]
     * </pre>
     * 之所以是 String 而不是结构化对象：库表存的就是这段 JSON 文本，
     * 保持原样透传可以避免一次「解析→再序列化」，也让前端能直接下发自己拼好的串。
     */
    private String options;

    /** 题目解析 */
    private String analysis;

    /**
     * 知识点/专题（V4），如「一元函数积分学」「语法」。
     *
     * <p>可留空（老题、临时录的题都不强求），但填了就能被专项训练与自由练习按专题抽到。
     * 长度与库表一致（VARCHAR(50)），在这里拦住超长值比让数据库截断更好。</p>
     */
    @Size(max = 50, message = "知识点最多 50 个字符")
    private String category;

    /** 0-禁用 1-启用；留空默认为启用 */
    private Integer status;
}