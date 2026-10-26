package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.constant.QuestionType;
import com.exam.dto.QuestionSnapshot;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.StudentAnswerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.exam.common.constant.Correctness.CORRECT;
import static com.exam.common.constant.Correctness.WRONG;
import static com.exam.common.constant.RecordStatus.GRADED;
import static com.exam.common.constant.RecordStatus.PENDING_GRADE;

/**
 * 判分的事务边界 —— 把「读写数据库」与「调 AI 评分」严格切成两段。
 *
 * <p><b>为什么单独开一个 bean 来承载事务，而不是继续在 {@link GradingEngine} 上写
 * {@code @Transactional}</b>：Spring 的声明式事务建立在<b>代理</b>之上，同一个类里
 * 「自己调自己」不经过代理，注解等于没写 —— 这是最容易被静默忽略的一类 bug。
 * 把事务方法放在另一个 bean（本类）里由 {@link GradingEngine} 跨 bean 调用，
 * 代理才会生效，事务边界才真的存在。</p>
 *
 * <p><b>为什么不用 {@code TransactionTemplate}</b>：两者都能解决自调用不走代理的问题，
 * 但 {@code TransactionTemplate} 要求把事务提交/回滚、异常处理写成代码块，
 * 判分这段逻辑本就是「一次读 + 一次写」的声明式描述，用注解更短、也更容易和
 * 项目里其它 Service 保持一致的写法。代价是多一个类，收益是事务边界一目了然：
 * 全项目的判分事务只在这一个文件里开启。</p>
 *
 * <p><b>两段事务之间刻意留白</b>：{@link #prepare} 提交之后、{@link #complete} 之前，
 * 调用方（{@link GradingEngine#grade}）在<b>没有任何事务</b>的情况下调用 AI。
 * 主观题每题最长 {@code app.ai.timeout-seconds}（默认 25 秒）且串行执行，
 * 若把这段等待圈进事务，一份主观题多的答卷会占着数据库连接好几分钟，
 * 交卷高峰时直接耗尽 HikariCP 连接池、判分吞吐崩塌。
 * AI 未启用或失败时，题目保留 {@code graded_by IS NULL}（待复核），记录停在「待判分」，
 * 由补偿任务（{@code sweepStuckPendingGradeRecords}）稍后重试。</p>
 *
 * <p><b>幂等</b>：只有「待判分」的记录才会被 {@link #prepare} 受理，重复投递的消息
 * 在这里就被拒掉，因此不会二次算分；收尾用条件更新推进状态
 * （{@code compareAndSetGradingResult}，并发重复触发也只有一个能把状态推进到
 * 「已出分」）。它刻意<b>不</b>写 {@code end_time} —— 判分不是学生交卷，
 * 重写交卷时间会让成绩单的「用时」随每轮重判往后跑。</p>
 */
@Slf4j
@Service
public class GradingTxService {

    private final StudentAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamMapper examMapper;
    private final ExamRuleService examRuleService;

    public GradingTxService(StudentAnswerMapper answerMapper,
                            QuestionMapper questionMapper,
                            ExamRecordMapper recordMapper,
                            ExamMapper examMapper,
                            ExamRuleService examRuleService) {
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.recordMapper = recordMapper;
        this.examMapper = examMapper;
        this.examRuleService = examRuleService;
    }

    /**
     * 事务一（短）：读状态、算客观题分数并落库、收集待 AI 评分的主观题。
     *
     * <p>刻意<b>不</b>做任何外部调用：这一步只碰数据库，应当在一瞬间结束。
     * 主观题在这里仅仅被「收集」成 {@link PendingSubjective}，真正的 AI 调用在事务提交之后。</p>
     *
     * @return 判分上下文；记录不存在或状态不是「待判分」时返回 {@code null}（幂等跳过）
     */
    @Transactional
    public GradingContext prepare(Long recordId) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            log.warn("判分跳过：记录不存在 recordId={}", recordId);
            return null;
        }
        // 幂等：只有「待判分」状态才处理，重复消息直接跳过
        if (!Integer.valueOf(PENDING_GRADE).equals(record.getStatus())) {
            log.info("判分跳过：状态不是待判分 recordId={}, status={}", recordId, record.getStatus());
            return null;
        }

        // 本卷每题分值快照：questionId -> 卷面满分
        Map<Long, Integer> fullScoreMap = loadSnapshotScores(record.getExamId());

        List<StudentAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, recordId));

        // 一次批量取题（而不是在循环里逐题 selectById）：一份 50 题的卷子原本要走 50 次往返
        Map<Long, Question> questions = loadQuestions(answers);

        int objectiveScore = 0;
        int correctCount = 0;
        List<StudentAnswer> objectiveUpdates = new ArrayList<>();
        List<PendingSubjective> pending = new ArrayList<>();

        for (StudentAnswer sa : answers) {
            Question question = questions.get(sa.getQuestionId());
            if (question == null) {
                // 题目已被删除：既不判分也不计入，与旧实现保持一致
                continue;
            }

            int fullScore = resolveFullScore(fullScoreMap, sa.getQuestionId(), question);

            if (QuestionType.isSubjective(question.getType())) {
                // 主观题只登记、不评分：AI 调用必须发生在事务之外，见类注释
                pending.add(new PendingSubjective(recordId, sa.getId(), question.getId(), fullScore,
                        question.getContent(), question.getAnswer(), sa.getAnswer()));
                continue;
            }

            String correctAnswer = GradingEngine.normalize(question.getType(), question.getAnswer());
            String studentAnswer = GradingEngine.normalize(question.getType(), sa.getAnswer());
            boolean correct = correctAnswer != null && correctAnswer.equals(studentAnswer);

            StudentAnswer update = new StudentAnswer();
            update.setId(sa.getId());
            update.setScore(correct ? fullScore : 0);
            update.setIsCorrect(correct ? CORRECT : WRONG);
            objectiveUpdates.add(update);

            objectiveScore += update.getScore();
            if (correct) {
                correctCount++;
            }
        }

        if (!objectiveUpdates.isEmpty()) {
            // 批量写分：逐题 update 在整卷客观题上是几十次往返，全部落在本事务内
            answerMapper.batchUpdateGradedScores(objectiveUpdates);
        }

        GradingContext context = new GradingContext(recordId, answers.size(),
                objectiveScore, correctCount, List.copyOf(pending));
        log.info("判分阶段一（事务内）完成: recordId={}, 客观题分={}, 待 AI 评分主观题={}",
                recordId, objectiveScore, pending.size());
        return context;
    }

    /**
     * 事务二（短）：把事务外拿到的 AI 分数逐题落库、汇总总分、推进记录状态。
     *
     * @param context  事务一的产出
     * @param aiScores 事务外 AI 评分结果，key 为作答行 id；未启用 AI 或评分失败的题不在其中
     * @return 判分摘要（口径与旧实现完全一致）
     */
    @Transactional
    public GradingEngine.GradeSummary complete(GradingContext context,
                                               Map<Long, AiGradingService.AiScore> aiScores) {
        List<StudentAnswer> aiUpdates = new ArrayList<>(context.pending().size());
        int aiScoreTotal = 0;
        int aiGraded = 0;
        int pendingReview = 0;

        for (PendingSubjective task : context.pending()) {
            AiGradingService.AiScore aiScore = aiScores.get(task.answerId());
            if (aiScore == null) {
                // AI 未启用或本题评分失败：不写任何评分结果。
                // 0 分是一个「评分结论」，而未评分是「没有结论」，
                // 混在一起会让「AI 挂了」表现成「学生全错」。
                pendingReview++;
                continue;
            }
            StudentAnswer update = new StudentAnswer();
            update.setId(task.answerId());
            update.setScore(aiScore.score());
            update.setIsCorrect(aiScore.correctness());
            update.setAiReason(aiScore.reason());
            update.setAiModel(aiScore.model());
            aiUpdates.add(update);

            aiScoreTotal += aiScore.score();
            aiGraded++;
        }

        if (!aiUpdates.isEmpty()) {
            answerMapper.batchUpdateAiScores(aiUpdates);
        }

        // 总分由已落库的各题得分汇总：客观题自动分 + AI 主观题分
        int totalScore = answerMapper.sumScoreByRecordId(context.recordId());

        // 还有题没评上（AI 未启用或失败）时才停在「待判分」，
        // 否则直接出分 —— AI 评分是自动的，学生交卷后应立即看到成绩
        int targetStatus = pendingReview > 0 ? PENDING_GRADE : GRADED;
        // ★ 用 compareAndSetGradingResult 而不是 compareAndSetSubmit：
        //   后者会把 end_time 置为 NOW()，而这里经常是**重复判分**（补偿任务重判、
        //   主观题还没评完所以状态仍停在「待判分」，条件 status=待判分 照样成立）。
        //   一旦它重写交卷时间，成绩单的「用时」就会随每轮重判往后跑
        //   —— 2026-09-18 在演示库实测到 1123 分钟（真实 56 分钟）。
        recordMapper.compareAndSetGradingResult(context.recordId(), totalScore, PENDING_GRADE, targetStatus);

        return new GradingEngine.GradeSummary(context.recordId(), totalScore, context.objectiveScore(),
                aiScoreTotal, context.correctCount(), context.totalCount(), pendingReview, aiGraded);
    }

    /**
     * 教师复核后重算总分。
     *
     * <p>只有两条短 SQL、不涉及任何外部调用，因此它本身没有长事务风险；
     * 仍然放在这里，是为了让「判分相关的事务开在哪里」只有一个答案。</p>
     */
    @Transactional
    public GradingEngine.GradeSummary regrade(Long recordId) {
        int totalScore = answerMapper.sumScoreByRecordId(recordId);
        // 同 complete()：复核是「教师改分」，也不是学生交卷，不能重写 end_time
        recordMapper.compareAndSetGradingResult(recordId, totalScore, PENDING_GRADE, GRADED);
        log.info("复核后重算总分 recordId={}, totalScore={}", recordId, totalScore);
        return new GradingEngine.GradeSummary(recordId, totalScore, 0, 0, 0, 0, 0, 0);
    }

    // ---------------------------------------------------------------- 内部

    /** 一次批量取回本卷全部题目：questionId -> 题目 */
    private Map<Long, Question> loadQuestions(List<StudentAnswer> answers) {
        Set<Long> questionIds = new LinkedHashSet<>();
        for (StudentAnswer sa : answers) {
            if (sa.getQuestionId() != null) {
                questionIds.add(sa.getQuestionId());
            }
        }
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        // 这里必须先转成 List 再传：QuestionMapper 自己声明了 selectBatchIds(List<Long>)，
        // 与 BaseMapper 的 selectBatchIds(Collection) 同名但<b>不是同一个 statement</b>，
        // 而该名字下注册的 SQL 用的是 @Param("ids")。传 Set 会解析到 Collection 那个重载，
        // 参数名对不上，直接抛 BindingException。
        List<Question> questions = questionMapper.selectBatchIds(new ArrayList<>(questionIds));
        if (questions == null || questions.isEmpty()) {
            return Map.of();
        }
        Map<Long, Question> map = new LinkedHashMap<>();
        for (Question question : questions) {
            map.putIfAbsent(question.getId(), question);
        }
        return map;
    }

    /** 读取某场考试的题目分值快照：questionId -> 卷面满分 */
    private Map<Long, Integer> loadSnapshotScores(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            return Map.of();
        }
        List<QuestionSnapshot> snapshot = examRuleService.parseSnapshot(exam.getQuestionSnapshot());
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (QuestionSnapshot item : snapshot) {
            if (item.getQuestionId() != null && item.getScore() != null) {
                map.put(item.getQuestionId(), item.getScore());
            }
        }
        return map;
    }

    /**
     * 取某题在本卷中的满分。
     *
     * <p>优先用试卷快照；快照缺失（历史数据没有快照字段）时退回题库默认分值，
     * 保证老数据仍然能被判分，而不是直接算 0 分。
     * 二者并不总是一样：教师在题库里把某题标成 5 分，组卷时可以按规则定为 10 分，
     * 误用题库分值会让一份全对的卷子拿不到卷面总分。</p>
     */
    private int resolveFullScore(Map<Long, Integer> snapshotScores, Long questionId, Question question) {
        Integer snapshotScore = snapshotScores.get(questionId);
        if (snapshotScore != null) {
            return snapshotScore;
        }
        log.warn("试卷快照缺少题目分值，退回题库默认分值 questionId={}", questionId);
        return question.getScore() == null ? 0 : question.getScore();
    }

    /** 一道等待 AI 评分的主观题：事务内只登记题面与答案，AI 调用在事务外 */
    public record PendingSubjective(Long recordId,
                                    Long answerId,
                                    Long questionId,
                                    int fullScore,
                                    String content,
                                    String referenceAnswer,
                                    String studentAnswer) {
    }

    /** 事务一的产出：客观题已落库，主观题待事务外评分 */
    public record GradingContext(Long recordId,
                                 int totalCount,
                                 int objectiveScore,
                                 int correctCount,
                                 List<PendingSubjective> pending) {
    }
}