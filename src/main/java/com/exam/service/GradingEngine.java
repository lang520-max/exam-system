package com.exam.service;

import com.exam.common.constant.QuestionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 判分引擎 —— 与消息队列解耦的核心判分逻辑。
 *
 * <p>刻意做成<b>不依赖 RabbitMQ 的普通 Service</b>，原因有两条：
 * <ol>
 *   <li>单元测试与 dev 档（无 MQ）都能直接调用它，判分规则不必为了跑测试而启动中间件；</li>
 *   <li>{@link GradingConsumer} 只是它的一个「触发入口」，判分规则本身保持单一实现，
 *       不会出现「MQ 里一套规则、同步调用里另一套规则」的漂移。</li>
 * </ol>
 *
 * <p><b>判分规则</b></p>
 * <ul>
 *   <li>单选 / 判断：与标准答案精确匹配（忽略大小写与空白）得满分，否则 0 分；</li>
 *   <li>多选：<b>全对才得分</b>，少选、多选、错选一律 0 分；选项顺序不影响结果；</li>
 *   <li>填空 / 简答（主观题）：交给 {@link AiGradingService} 评分。未启用 AI 或调用失败时
 *       保留 {@code graded_by = NULL}（即「待复核」），<b>不写 0 分</b>——
 *       0 分是一个评分结论，而「未评分」没有结论；
 *       混在一起会让「AI 挂了」表现成「学生全错」；</li>
 *   <li>未作答：0 分。</li>
 * </ul>
 *
 * <p><b>幂等</b>：只有状态为「待判分」的记录才会被判分，重复投递的消息不会二次算分。</p>
 *
 * <p><b>本类刻意不带 {@code @Transactional}</b>：判分是「写库 → 调 AI → 再写库」三段，
 * 中间那段 AI 调用（每题最长 {@code app.ai.timeout-seconds}，N 题串行）必须发生在事务之外，
 * 否则一份主观题多的答卷会长时间占着数据库连接。两个事务边界都在
 * {@link GradingTxService} 这个<b>另一个 bean</b> 里 —— 见该类注释里关于
 * 「自调用不走代理」的说明。</p>
 */
@Slf4j
@Service
public class GradingEngine {

    private final GradingTxService gradingTxService;
    private final AiGradingService aiGradingService;

    public GradingEngine(GradingTxService gradingTxService,
                         AiGradingService aiGradingService) {
        this.gradingTxService = gradingTxService;
        this.aiGradingService = aiGradingService;
    }

    /**
     * 对一份答卷执行判分。
     *
     * <p>三段式，事务边界见 {@link GradingTxService}：</p>
     * <ol>
     *   <li><b>短事务</b>：校验状态仍是待判分、按试卷快照算客观题分并落库，
     *       同时把待 AI 评分的主观题收集成列表；</li>
     *   <li><b>事务之外</b>：逐题调用 AI。这里没有事务在身 —— AI 慢不该拖住数据库连接；</li>
     *   <li><b>短事务</b>：把 AI 分数落库、汇总总分、条件更新推进状态。</li>
     * </ol>
     *
     * <p><b>分值口径</b>：每题的满分取自「试卷快照」而不是题库的默认分值。
     * 二者并不总是一样：教师在题库里把某题标成 5 分，组卷时可以按规则把它定为 10 分。
     * 若判分时误用题库分值，一份全对的卷子会只拿到题库分值之和
     * （例如卷面 70 分却只得 40 分），学生成绩与试卷总分对不上。
     * 试卷快照才是「这份卷子每题值多少分」的唯一真相。</p>
     *
     * <p><b>降级</b>：AI 未启用或某题评分失败时，该题不写任何分数
     * （{@code graded_by} 仍为 NULL，即待复核），记录停在「待判分」，
     * 客观题分数照常保留，稍后由补偿任务重判。</p>
     *
     * @param recordId 考试记录 ID
     * @return 判分结果摘要；记录不存在或状态不是「待判分」时返回 null（幂等跳过）
     */
    public GradeSummary grade(Long recordId) {
        // ① 短事务：只碰数据库
        GradingTxService.GradingContext context = gradingTxService.prepare(recordId);
        if (context == null) {
            return null;
        }

        // ② 事务之外：AI 调用。绝不能把这段包进事务里（见类注释）
        Map<Long, AiGradingService.AiScore> aiScores = gradeSubjective(context);

        // ③ 短事务：写 AI 分数 + 汇总总分 + 推进状态
        GradeSummary summary = gradingTxService.complete(context, aiScores);
        log.info("判分完成 {}", summary);
        return summary;
    }

    /**
     * 事务外的主观题评分。
     *
     * <p>失败（返回 null）的题不进结果 map，由 {@link GradingTxService#complete}
     * 判定为「待复核」——返回 null 而不是 0 分很重要：0 分是一个<b>评分结论</b>，
     * 而未评分是<b>没有结论</b>，两者混在一起会让「AI 挂了」表现成「学生全错」。</p>
     */
    private Map<Long, AiGradingService.AiScore> gradeSubjective(GradingTxService.GradingContext context) {
        List<GradingTxService.PendingSubjective> pending = context.pending();
        if (pending.isEmpty()) {
            return Map.of();
        }
        // AI 未启用时一次调用都不发：这些题保持「待复核」，交卷不受影响
        if (!aiGradingService.isEnabled()) {
            log.info("AI 评分未启用，{} 道主观题保留为待复核: recordId={}",
                    pending.size(), context.recordId());
            return Map.of();
        }

        Map<Long, AiGradingService.AiScore> results = new LinkedHashMap<>();
        for (GradingTxService.PendingSubjective task : pending) {
            AiGradingService.AiScore aiScore = aiGradingService.grade(
                    task.content(), task.referenceAnswer(), task.fullScore(), task.studentAnswer());
            if (aiScore == null) {
                log.warn("AI 评分未成功，本题保留为待复核: recordId={}, questionId={}",
                        context.recordId(), task.questionId());
                continue;
            }
            results.put(task.answerId(), aiScore);
        }
        return results;
    }

    /**
     * 教师复核主观题后重算总分。
     *
     * <p>不涉及 AI 调用，因此没有长事务风险；事务边界同样收在
     * {@link GradingTxService#regrade} 里，保证「判分的事务开在哪里」只有一个答案。</p>
     */
    public GradeSummary regrade(Long recordId) {
        return gradingTxService.regrade(recordId);
    }

    /**
     * 答案规范化：把「同一个意思的不同写法」收敛成同一个字符串再比较。
     *
     * <p>这样学生用 {@code "a"}、{@code " A "}、{@code "A."} 作答都能被正确识别；
     * 多选题的 {@code "C,A"} 会排序去重成 {@code "AC"}，不因书写顺序被判错。</p>
     *
     * @return 规范化后的答案；空白或无法识别时返回 null（视为未作答）
     */
    public static String normalize(Integer type, String rawAnswer) {
        if (rawAnswer == null) {
            return null;
        }
        String value = rawAnswer.trim().toUpperCase(java.util.Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (Integer.valueOf(QuestionType.JUDGE).equals(type)) {
            if (JUDGE_TRUE.contains(value)) {
                return "A";
            }
            if (JUDGE_FALSE.contains(value)) {
                return "B";
            }
            // 判断题只有「正确 / 错误」两种可能，别的写法一律视为无效作答。
            // 若不在这里返回 null，就会掉到下面的「只留字母」分支，
            // 把 "maybe" 变成 "MAYBE" 这种看似有值的脏数据。
            return null;
        }
        if (Integer.valueOf(QuestionType.MULTIPLE).equals(type)) {
            // 只保留字母，排序 + 去重，使 "C,A" / "a c" / "AC" 三种写法等价
            char[] chars = value.replaceAll("[^A-Z]", "").toCharArray();
            Arrays.sort(chars);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) {
                if (sb.length() == 0 || sb.charAt(sb.length() - 1) != c) {
                    sb.append(c);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        // 单选 / 判断：剥掉 "A." "A、" 之类的修饰，只留字母
        String cleaned = value.replaceAll("[^A-Z]", "");
        return cleaned.isEmpty() ? value : cleaned;
    }

    /** 判断题「正确」的各种写法，统一归一化为 A */
    private static final java.util.Set<String> JUDGE_TRUE = java.util.Set.of(
            "A", "T", "TRUE", "Y", "YES", "对", "正确", "是", "√");

    /** 判断题「错误」的各种写法，统一归一化为 B */
    private static final java.util.Set<String> JUDGE_FALSE = java.util.Set.of(
            "B", "F", "FALSE", "N", "NO", "错", "错误", "否", "×", "X");

    /** 判分结果摘要 */
    public record GradeSummary(Long recordId,
                               int totalScore,
                               int objectiveScore,
                               int aiScore,
                               int correctCount,
                               int totalCount,
                               int pendingReviewCount,
                               int aiGradedCount) {
    }
}