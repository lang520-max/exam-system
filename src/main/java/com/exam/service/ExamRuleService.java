package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.dto.QuestionSnapshot;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.QuestionBankMapper;
import com.exam.mapper.QuestionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能组卷服务 —— 按题型 + 难度权重随机抽题。
 *
 * <p><b>算法思路</b>（{@link #pickByDifficulty}）：</p>
 * <ol>
 *   <li>按题型逐条处理规则（如 2 道单选、2 道多选、2 道判断）；</li>
 *   <li>单条规则内按难度权重 <b>简单:中等:困难 = 5:3:2</b> 分配名额；</li>
 *   <li>每个难度用 {@code ORDER BY RAND() LIMIT n} 从题库抽题；</li>
 *   <li>某难度库存不足时<b>向相邻难度借调</b>名额，而不是直接失败——
 *       小题库（例如只有 2 道简单题）也能满足「抽 5 道」的规则；</li>
 *   <li>借调后仍不足，才整卷失败并明确告知「需要几道、实际几道」。</li>
 * </ol>
 *
 * <p><b>ORDER BY RAND() 的性能取舍</b>：RAND() 无法走索引排序，全表扫描代价随题量线性增长。
 * 这里的优化是先用联合索引 {@code idx_bank_type / idx_type_difficulty} 把候选集压到
 * 「某题库 + 某题型 + 某难度」这一小片，再在这一小片内做随机排序，
 * 而不是对整张 question 表排序。题量再上一个量级时，应改为
 * 「随机 ID 区间 + 补充查询」或把候选 ID 放进 Redis SET 用 SRANDMEMBER 抽。</p>
 */
@Slf4j
@Service
public class ExamRuleService {

    /** 难度权重：简单 50% */
    private static final double RATIO_EASY = 0.5;
    /** 难度权重：中等 30% */
    private static final double RATIO_MEDIUM = 0.3;
    /** 难度权重：困难 20%（余数补齐，避免四舍五入后总数对不上） */

    private static final int DIFFICULTY_EASY = 1;
    private static final int DIFFICULTY_MEDIUM = 2;
    private static final int DIFFICULTY_HARD = 3;

    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final ExamMapper examMapper;
    private final ObjectMapper objectMapper;

    public ExamRuleService(QuestionBankMapper bankMapper,
                           QuestionMapper questionMapper,
                           ExamMapper examMapper,
                           ObjectMapper objectMapper) {
        this.bankMapper = bankMapper;
        this.questionMapper = questionMapper;
        this.examMapper = examMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 智能组卷：按规则抽题、落库试卷与题目快照。
     *
     * @param form   组卷参数
     * @param userId 创建人（教师）
     * @return 生成的考试（草稿状态）
     */
    @Transactional
    public Exam generateExam(ExamGenerateDTO form, Long userId) {
        QuestionBank bank = bankMapper.selectById(form.getBankId());
        if (bank == null) {
            throw new BusinessException(404, "题库不存在");
        }

        // 同一题型只允许一条规则，否则两条规则可能抽到同一道题，也让「每题分值」变得含糊
        Set<Integer> seenTypes = new HashSet<>();
        List<QuestionSnapshot> snapshot = new ArrayList<>();
        int totalScore = 0;

        for (ExamGenerateDTO.RuleItem rule : form.getRules()) {
            if (rule.getCount() == null || rule.getCount() <= 0) {
                continue;
            }
            if (!seenTypes.add(rule.getType())) {
                throw new BusinessException("同一题型只能配置一条抽题规则，请合并后再试");
            }

            List<Long> pickedIds = pickByDifficulty(form.getBankId(), rule.getType(), rule.getCount(), rule.getCategory());
            if (pickedIds.size() < rule.getCount()) {
                String cat = (rule.getCategory() == null || rule.getCategory().isBlank())
                        ? null : rule.getCategory().trim();
                throw new BusinessException("题库中「" + bank.getName() + "」的「"
                        + QuestionTypeText.of(rule.getType()) + "」"
                        + (cat == null ? "" : "、专题「" + cat + "」")
                        + "数量不足：需要 " + rule.getCount() + " 道，实际只有 " + pickedIds.size() + " 道"
                        + (cat == null ? "" : "。专题抽题不会跨专题借题（借来的题不属于这个专题），请减少题量或换一个专题"));
            }

            for (Long questionId : pickedIds) {
                snapshot.add(new QuestionSnapshot(questionId, rule.getScore()));
                totalScore += rule.getScore();
            }
        }

        if (snapshot.isEmpty()) {
            throw new BusinessException("组卷失败：未抽到任何题目，请检查题库是否为空");
        }

        // 打乱题号顺序，避免同一题型的题目扎堆出现
        Collections.shuffle(snapshot);

        int passScore = form.getPassScore() != null && form.getPassScore() > 0
                ? form.getPassScore() : (int) Math.ceil(totalScore * 0.6);
        if (passScore > totalScore) {
            throw new BusinessException("及格分（" + passScore + "）不能高于试卷总分（" + totalScore + "）");
        }

        Exam exam = new Exam();
        exam.setTitle(form.getTitle());
        exam.setDescription(form.getDescription());
        exam.setBankId(form.getBankId());
        exam.setDuration(form.getDuration());
        exam.setTotalScore(totalScore);
        exam.setPassScore(passScore);
        // V4：试卷类型（模拟卷 / 专项训练）。留空按模拟卷 —— 老客户端不带这个字段时行为不变
        exam.setPaperType(form.getPaperType() == null || form.getPaperType().isBlank()
                ? "模拟卷" : form.getPaperType().trim());
        exam.setStatus(0);
        exam.setCreatedBy(userId);
        try {
            exam.setRuleConfig(objectMapper.writeValueAsString(form.getRules()));
            exam.setQuestionSnapshot(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            throw new BusinessException("组卷规则序列化失败");
        }

        examMapper.insert(exam);
        log.info("组卷完成 examId={}, 题数={}, 总分={}", exam.getId(), snapshot.size(), totalScore);
        return examMapper.selectById(exam.getId());
    }

    /**
     * 取考试的题目（不含正确答案），按快照还原卷面顺序与分值。
     *
     * <p>分值用快照值覆盖题库默认分值，保证判分口径与组卷时一致。</p>
     */
    public List<Question> getExamQuestions(Exam exam) {
        List<QuestionSnapshot> snapshot = parseSnapshot(exam.getQuestionSnapshot());
        if (snapshot.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = snapshot.stream().map(QuestionSnapshot::getQuestionId).toList();
        Map<Long, Question> byId = questionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<Question> ordered = new ArrayList<>(snapshot.size());
        for (QuestionSnapshot item : snapshot) {
            Question question = byId.get(item.getQuestionId());
            if (question == null) {
                // 题目被删除：跳过而不是抛异常，避免一份卷子因单题缺失而完全无法作答
                log.warn("试卷快照中的题目已不存在 examId={}, questionId={}",
                        exam.getId(), item.getQuestionId());
                continue;
            }
            // 用卷面快照分值覆盖题库分值
            question.setScore(item.getScore());
            ordered.add(question);
        }
        return ordered;
    }

    /** 取考试的题目（不含正确答案） */
    public List<Question> getExamQuestions(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        return getExamQuestions(exam);
    }

    /** 解析题目快照 JSON，解析失败时返回空列表而不是抛异常 */
    public List<QuestionSnapshot> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionSnapshot>>() {});
        } catch (Exception e) {
            log.error("解析题目快照失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 按难度权重抽题：5:3:2 分配名额，库存不足时向相邻难度借调。
     *
     * <p>包级可见（package-private）以便单元测试直接验证分配算法，
     * 不必启动 Spring 上下文。</p>
     */
    List<Long> pickByDifficulty(Long bankId, Integer type, int count) {
        return pickByDifficulty(bankId, type, count, null);
    }

    /**
     * 同上，但可以把抽题范围**限制在一个知识点内**（V4）。
     *
     * <p>「一元函数积分学专项训练」这类卷子就是靠它：难度之间仍会互相借调
     * （同一个专题里简单题多了、难题少了，是正常现象），但**绝不跨专题借题** ——
     * 借来的题不属于这个专题，「专项」就名不副实了。因此这里不像难度那样
     * 去别的专题兜底：不够就让调用方报错说清楚。</p>
     *
     * @param category 知识点；null / 空白表示不限（与 V4 之前的行为完全一致）
     */
    List<Long> pickByDifficulty(Long bankId, Integer type, int count, String category) {
        String cat = (category == null || category.isBlank()) ? null : category.trim();

        int availableEasy = questionMapper.countForPick(bankId, type, DIFFICULTY_EASY, cat);
        int availableMedium = questionMapper.countForPick(bankId, type, DIFFICULTY_MEDIUM, cat);
        int availableHard = questionMapper.countForPick(bankId, type, DIFFICULTY_HARD, cat);

        int[] quota = allocate(count, availableEasy, availableMedium, availableHard);

        // 用 LinkedHashSet 兜底去重：即使重试或边界情况下抽重，也不会让同一道题出现两次
        Set<Long> picked = new LinkedHashSet<>();
        picked.addAll(questionMapper.selectRandomForPick(bankId, type, DIFFICULTY_EASY, cat, quota[0]));
        picked.addAll(questionMapper.selectRandomForPick(bankId, type, DIFFICULTY_MEDIUM, cat, quota[1]));
        picked.addAll(questionMapper.selectRandomForPick(bankId, type, DIFFICULTY_HARD, cat, quota[2]));

        // 各难度都借调完仍不足时，从「同一范围内」剩余题目里补抽
        // （difficulty 传 null = 不限难度，但 category 仍然生效）
        if (picked.size() < count) {
            List<Long> extras = questionMapper.selectRandomForPick(bankId, type, null, cat, count - picked.size());
            picked.addAll(extras);
        }

        return new ArrayList<>(picked);
    }

    /**
     * 把 count 按 5:3:2 分配到三个难度，并在某个难度库存不足时向其他难度借调。
     *
     * <p>算法分两步，先「按比例算初值并按上限裁剪」，再「把剩余名额补给还有空位的难度」。
     * 这样能保证三条性质：</p>
     * <ol>
     *   <li>各难度抽题数 <b>不超过</b>各自库存；</li>
     *   <li>总量 <b>不超过</b> count；</li>
     *   <li>只要总库存够，总量就 <b>恰好等于</b> count（借调而不是直接失败）。</li>
     * </ol>
     *
     * <p>抽成静态纯函数是为了能穷举边界情况做单元测试，
     * 不必为了验证一次借调去造一整个题库。</p>
     *
     * @return {@code [easy, medium, hard]} 三个难度各自应抽的数量
     */
    static int[] allocate(int count, int availEasy, int availMedium, int availHard) {
        if (count <= 0) {
            return new int[]{0, 0, 0};
        }
        // 库存理论上不会是负数，这里兜一层，避免调用方传错值导致后续减法出错
        int[] cap = {Math.max(0, availEasy), Math.max(0, availMedium), Math.max(0, availHard)};

        // 第一步：按 5:3:2 算初值。hard 用余数补齐，避免三次四舍五入后总数对不上 count
        int easy = (int) Math.round(count * RATIO_EASY);
        int medium = (int) Math.round(count * RATIO_MEDIUM);
        int hard = count - easy - medium;
        if (hard < 0) {
            medium += hard;
            hard = 0;
        }

        int[] quota = {easy, medium, hard};
        // 按各自库存上限裁剪
        for (int i = 0; i < quota.length; i++) {
            quota[i] = Math.min(Math.max(quota[i], 0), cap[i]);
        }

        // 第二步：被裁掉的名额，补给还有空位的难度（简单 → 中等 → 困难）
        int remaining = count - (quota[0] + quota[1] + quota[2]);
        for (int i = 0; i < quota.length && remaining > 0; i++) {
            int room = cap[i] - quota[i];
            int add = Math.min(room, remaining);
            quota[i] += add;
            remaining -= add;
        }
        return quota;
    }

    /** 题型中文名 */
    private static final class QuestionTypeText {
        static String of(Integer type) {
            if (type == null) {
                return "未知";
            }
            return switch (type) {
                case 0 -> "单选题";
                case 1 -> "多选题";
                case 2 -> "判断题";
                case 3 -> "填空题";
                case 4 -> "简答题";
                default -> "未知";
            };
        }
    }
}