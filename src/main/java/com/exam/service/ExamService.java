package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.common.utils.AfterCommitExecutor;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.dto.response.ExamResultVO;
import com.exam.dto.response.ExamStatsVO;
import com.exam.dto.response.MyRecordVO;
import com.exam.dto.response.QuestionVO;
import com.exam.entity.*;
import com.exam.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.exam.common.constant.Correctness.CORRECT;
import static com.exam.common.constant.RecordStatus.*;

/**
 * 考试核心服务 —— 开考、答题、交卷、成绩
 *
 * <p>核心难点：
 * <ol>
 *   <li><b>Redis 倒计时</b>：开考时在 Redis 设置 TTL，WebSocket 每秒推送剩余时间</li>
 *   <li><b>幂等防重复提交</b>：条件更新 + 唯一约束 + Redis SETNX 三重保险</li>
 *   <li><b>RabbitMQ 异步判分</b>：交卷后将判分任务发送到消息队列</li>
 * </ol>
 */
@Slf4j
@Service
public class ExamService {

    private static final String EXAM_TIMEOUT_KEY = "exam:timeout:%d:%d";
    private static final String IDEMPOTENT_KEY = "idempotent:submit:%s:%s";

    /**
     * 交卷宽限秒数。
     *
     * <p>容忍两件事：网络往返延迟；以及前端收到 WebSocket 的 TIMEOUT 推送后
     * 再发起自动交卷请求的那一小段时间。没有这个宽限，倒计时到点的
     * 自动交卷会因为「刚好晚了几百毫秒」被判成超时而失败。</p>
     */
    private static final long SUBMIT_GRACE_SECONDS = 30;

    /**
     * 「待判分」停留多久算异常、需要兜底重判（秒）。
     *
     * <p>正常判分是秒级的；超过这个时间仍停在待判分，说明判分消息丢了、
     * 或者消费者在提交前读到旧状态而误跳过。补偿任务默认每 2 分钟扫一次。</p>
     */
    private static final long PENDING_GRADE_STUCK_SECONDS = 120;

    private final ExamMapper examMapper;
    private final ExamRecordMapper recordMapper;
    private final QuestionMapper questionMapper;
    private final StudentAnswerMapper answerMapper;
    private final ExamRuleService examRuleService;
    private final GradingEngine gradingEngine;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    /**
     * 是否真的启用了 RabbitMQ（来自 {@code app.rabbitmq.enabled}）。
     *
     * <p>★ 存在的理由：不能拿「{@code rabbitTemplate != null}」当判据。
     * Spring Boot 的 RabbitAutoConfiguration 只要 classpath 上有
     * spring-boot-starter-amqp 就会无条件提供 RabbitTemplate ——
     * 于是「关掉 MQ 就同步判分」这条降级路径永远不会触发：
     * 交卷会照旧往 MQ 投消息、连接失败、只打一行 ERROR，成绩要等
     * 「待判分补偿任务」（每 2 分钟一次）才出来。
     * 2026-09-16 在只有 2 GiB、刻意不装 RabbitMQ 的云主机上实测踩到。</p>
     */
    private final boolean rabbitEnabled;

    /** 人脸核验门禁（开考前必须通过；app.face.enabled=false 时门禁不生效） */
    private final FaceRecognitionService faceService;

    public ExamService(ExamMapper examMapper,
                       ExamRecordMapper recordMapper,
                       QuestionMapper questionMapper,
                       StudentAnswerMapper answerMapper,
                       ExamRuleService examRuleService,
                       GradingEngine gradingEngine,
                       @Autowired(required = false) RabbitTemplate rabbitTemplate,
                       @Autowired(required = false) StringRedisTemplate redisTemplate,
                       @Value("${app.rabbitmq.enabled:false}") boolean rabbitEnabled,
                       FaceRecognitionService faceService) {
        this.examMapper = examMapper;
        this.recordMapper = recordMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.examRuleService = examRuleService;
        this.gradingEngine = gradingEngine;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.rabbitEnabled = rabbitEnabled;
        this.faceService = faceService;
    }

    /**
     * 开始考试
     *
     * <p>幂等处理：同一学生同一考试只允许一条记录。
     * 若还在答题中则返回原记录（不重置计时），已交卷则拒绝重复开考。</p>
     */
    @Transactional
    public ExamRecord startExam(Long examId, Long userId, String ipAddress) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        if (!exam.isPublished()) {
            throw new BusinessException("该考试尚未发布或已结束");
        }

        // 考试时间窗。字段为空表示「这一端不限制」。
        // 没有这段校验时 start_time / end_time 只是两个谁也不读的字段：
        // 学生在发布后任意时刻都能开考，也没有「到点自动结束」。
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BusinessException(409, "考试尚未开始，开始时间 " + exam.getStartTime());
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new BusinessException(409, "考试已结束，结束时间 " + exam.getEndTime());
        }

        // 检查已有记录
        ExamRecord existing = recordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getUserId, userId));
        if (existing != null) {
            if (existing.getStatus() >= GRADED) {
                throw new BusinessException("你已经参加过这场考试，不能重复开考");
            }
            // 已在考试中的记录直接返回：这种情况开考时已经过人脸核验了，
            // 不该因为「核验结果已过有效窗口」把正在考试的学生挡在外面。
            return existing;
        }

        // ★ 人脸核验门禁：只拦「新建记录」这一次开考行为。
        //   判据是 face_verify_log 里近期的 PASS/OVERRIDE_PASS（查库不查内存），
        //   因此前端即使绕过界面直接调本接口，也过不去。
        //   app.face.enabled=false 时 hasValidPass 恒为 true（门禁整体不生效）。
        if (!faceService.hasValidPass(userId, examId)) {
            throw new BusinessException(403, "请先通过人脸核验再开始考试（核验结果有效期 "
                    + faceService.status(userId, examId).getPassWindowSeconds() / 60 + " 分钟）");
        }

        ExamRecord record = new ExamRecord();
        record.setExamId(examId);
        record.setUserId(userId);
        record.setStartTime(LocalDateTime.now());
        record.setStatus(ONGOING);
        record.setIpAddress(ipAddress);

        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return recordMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                            .eq(ExamRecord::getExamId, examId)
                            .eq(ExamRecord::getUserId, userId));
        }

        // 把核验结论固化到答卷上（成绩单/教师端展示用；人工放行时相似度为 null）
        faceService.markRecordVerified(record.getId(), faceService.latestPassConfidence(userId, examId));

        // 设置 Redis 倒计时（Redis 不可用时跳过）
        if (redisTemplate != null) {
            String timeoutKey = String.format(EXAM_TIMEOUT_KEY, examId, userId);
            redisTemplate.opsForValue().set(timeoutKey, String.valueOf(exam.getDuration() * 60),
                    exam.getDuration() * 60, TimeUnit.SECONDS);
        }

        return recordMapper.selectById(record.getId());
    }

    /**
     * 获取考试题目（不含正确答案）
     */
    public List<QuestionVO> getExamQuestions(Long recordId, Long userId) {
        ExamRecord record = validateRecordAccess(recordId, userId);
        if (record.getStatus() != ONGOING) {
            throw new BusinessException("该考试已交卷，不能继续答题");
        }

        List<Question> questions = examRuleService.getExamQuestions(record.getExamId());
        return QuestionVO.from(questions);
    }

    /**
     * 提交答卷 —— 异步判分
     *
     * <p>将判分任务发送到 RabbitMQ，由消费者异步处理。
     * 客观题自动判分，主观题标记待人工复核。</p>
     */
    @Transactional
    public Map<String, Object> submitExam(ExamSubmitDTO form, Long userId, String requestId) {
        // 幂等校验：Redis SETNX（Redis 不可用时跳过）
        if (redisTemplate != null) {
            String idempotentKey = String.format(IDEMPOTENT_KEY, userId, requestId);
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", Duration.ofSeconds(30));
            if (Boolean.FALSE.equals(acquired)) {
                throw new BusinessException("请勿重复提交");
            }
        }

        // 校验记录
        ExamRecord record = recordMapper.selectById(form.getRecordId());
        if (record == null) {
            throw new BusinessException(404, "考试记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能提交自己的答卷");
        }
        if (record.getStatus() == TIMEOUT) {
            // 已被定时任务扫成超时，单独给一条准确的提示，
            // 否则会落进下面的「已交卷」分支，让学生以为是自己重复提交了
            throw new BusinessException(409, "考试已超时，无法交卷");
        }
        if (record.getStatus() != ONGOING) {
            // 409 Conflict：请求本身合法，但与服务端当前状态冲突（已经交过卷了）
            throw new BusinessException(409, "该考试已交卷，请勿重复提交");
        }

        // 取试卷：时限校验需要 duration
        Exam exam = examMapper.selectById(record.getExamId());
        if (exam == null) {
            throw new BusinessException(404, "试卷不存在");
        }

        // ★ 考试时限校验 —— 必须放在抢占交卷权【之前】。
        // 若放在 CAS 之后，超时的请求会先把记录改成「待判分」再被拒绝，
        // 记录就卡在一个既没交卷、也不在考试中的状态里。
        assertWithinDeadline(record, exam);

        // 条件更新抢占交卷权（防并发重复提交的第二道保险）
        int changed = recordMapper.compareAndSetSubmit(
                record.getId(), 0, ONGOING, PENDING_GRADE);
        if (changed == 0) {
            throw new BusinessException(409, "该考试已交卷，请勿重复提交");
        }

        // 保存学生答案 —— 题目范围严格取「试卷快照」，而不是题库全量。
        // 随机组卷下题库里会有大量没被这份卷子抽中的题，若按题库取题，
        // 成绩单会凭空多出几十道「未作答」的题，总分口径也全错。
        List<Question> questions = examRuleService.getExamQuestions(exam);

        Map<Long, String> submitted = form.getAnswers() == null
                ? Collections.emptyMap() : form.getAnswers();
        applySubmittedAnswers(record.getId(), questions, submitted);

        // 发送异步判分任务到 RabbitMQ；MQ 未启用时走同步判分
        // ★ 判据必须是 app.rabbitmq.enabled，而不是「有没有 RabbitTemplate bean」：
        //   RabbitAutoConfiguration 总会提供 RabbitTemplate，用它当判据会让降级永不生效
        //   （详见 rabbitEnabled 字段的注释）。
        if (rabbitEnabled && rabbitTemplate != null) {
            Map<String, Object> task = new HashMap<>();
            task.put("recordId", record.getId());
            task.put("examId", record.getExamId());
            task.put("userId", userId);
            // ★ 必须等**事务提交之后**再发。
            // 消费者在另一个线程 / 连接 / 事务里读数据：若消息在提交前就投递出去，
            // 它看到的仍是「考试中(0)」的旧状态，grade() 的幂等判断会直接 return
            // 跳过，而消费者把「跳过」当成功 ack 掉 —— 这份答卷就永久停在
            // 「待判分」，学生永远看不到成绩，且没有任何东西会再碰它。
            // （兜底：sweepStuckPendingGradeRecords）
            AfterCommitExecutor.afterCommit(() -> sendGradingTask(task));
        } else {
            log.info("RabbitMQ 未启用，执行同步判分: recordId={}", record.getId());
            gradingEngine.grade(record.getId());
        }

        // 删除 Redis 倒计时
        if (redisTemplate != null) {
            String timeoutKey = String.format(EXAM_TIMEOUT_KEY, record.getExamId(), userId);
            redisTemplate.delete(timeoutKey);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("status", "PENDING_GRADE");
        result.put("message", "提交成功，客观题自动判分中，主观题待教师复核");
        return result;
    }

    /**
     * 把交卷时的最终作答落到 {@code student_answer}（存在则更新、不存在则插入）。
     *
     * <p><b>为什么不能用 batchInsert 一把插</b>：答题过程中前端会周期性暂存答案
     * （见 {@link #saveDraftAnswers}），那些行已经在表里了，而
     * {@code student_answer} 上有唯一键 {@code (record_id, question_id)} ——
     * 纯插入会直接唯一键冲突，表现成「学生一交卷就 500」，且答案还没保存。
     * 这是加暂存功能时必须同步改掉的地方。</p>
     *
     * <p>未作答的题也会写一行（{@code answer = null}）：成绩单要按快照逐题展示
     * 「未作答」，而不是缺一行。</p>
     */
    private void applySubmittedAnswers(Long recordId, List<Question> questions, Map<Long, String> submitted) {
        Map<Long, StudentAnswer> existing = answerMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                                .eq(StudentAnswer::getRecordId, recordId)).stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, a -> a, (a, b) -> a));

        List<StudentAnswer> toInsert = new ArrayList<>();
        for (Question q : questions) {
            String value = submitted.get(q.getId());
            StudentAnswer row = existing.get(q.getId());
            if (row == null) {
                StudentAnswer sa = new StudentAnswer();
                sa.setRecordId(recordId);
                sa.setQuestionId(q.getId());
                sa.setAnswer(value);
                sa.setScore(0);
                sa.setIsCorrect(0);
                toInsert.add(sa);
            } else {
                // 只改作答文本；评分结论（score/is_correct/graded_by）留给判分引擎写
                answerMapper.updateAnswerText(row.getId(), value);
            }
        }
        if (!toInsert.isEmpty()) {
            answerMapper.batchInsert(toInsert);
        }
    }

    /**
     * 暂存答案（答题过程中自动保存）。
     *
     * <p><b>为什么必须有</b>：在此之前作答只存在浏览器内存里，刷新页面、浏览器崩溃、
     * 网络抖动、或被超时任务命中，学生答了 45 分钟的内容全部作废 —— 对考试系统
     * 这是最伤的一类可靠性缺口。</p>
     *
     * <p>三条边界：</p>
     * <ol>
     *   <li>只允许「考试中」暂存：已交卷/已超时的答卷再改作答，会让成绩单与判分对不上；</li>
     *   <li>只接受**本卷快照里**的题目：否则脏数据会让成绩单凭空多出几道「未作答」的题；</li>
     *   <li>只写 {@code answer}，不碰任何评分字段 —— 暂存不等于给自己打分。</li>
     * </ol>
     *
     * @return {@code {saved: 本次写入条数, savedAt: 服务端毫秒时间戳}}
     */
    @Transactional
    public Map<String, Object> saveDraftAnswers(Long recordId, Long userId, Map<Long, String> answers) {
        ExamRecord record = validateRecordAccess(recordId, userId);
        if (!Integer.valueOf(ONGOING).equals(record.getStatus())) {
            throw new BusinessException(409, "该考试已交卷，无法再暂存答案");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("saved", 0);
        if (answers == null || answers.isEmpty()) {
            return result;
        }

        Exam exam = examMapper.selectById(record.getExamId());
        if (exam == null) {
            throw new BusinessException(404, "试卷不存在");
        }
        Set<Long> allowed = examRuleService.parseSnapshot(exam.getQuestionSnapshot()).stream()
                .map(com.exam.dto.QuestionSnapshot::getQuestionId)
                .collect(Collectors.toSet());

        Map<Long, StudentAnswer> existing = answerMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                                .eq(StudentAnswer::getRecordId, recordId)).stream()
                .collect(Collectors.toMap(StudentAnswer::getQuestionId, a -> a, (a, b) -> a));

        List<StudentAnswer> toInsert = new ArrayList<>();
        int updated = 0;
        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            Long questionId = entry.getKey();
            if (questionId == null || !allowed.contains(questionId)) {
                continue;   // 不属于本卷的题直接忽略，避免脏行
            }
            String value = entry.getValue();
            StudentAnswer row = existing.get(questionId);
            if (row == null) {
                StudentAnswer sa = new StudentAnswer();
                sa.setRecordId(recordId);
                sa.setQuestionId(questionId);
                sa.setAnswer(value);
                sa.setScore(0);
                sa.setIsCorrect(0);
                toInsert.add(sa);
            } else if (!Objects.equals(row.getAnswer(), value)) {
                answerMapper.updateAnswerText(row.getId(), value);
                updated++;
            }
        }
        if (!toInsert.isEmpty()) {
            answerMapper.batchInsert(toInsert);
        }

        result.put("saved", toInsert.size() + updated);
        result.put("savedAt", System.currentTimeMillis());
        return result;
    }

    /**
     * 读取暂存答案 —— 刷新 / 断线重连后回填用。
     *
     * <p>返回的是**暂存**而非「已提交作答」：交卷后此接口返回 409，
     * 因为那时作答已经定稿，该看的是成绩单而不是答题页。</p>
     */
    public Map<String, Object> getDraftAnswers(Long recordId, Long userId) {
        ExamRecord record = validateRecordAccess(recordId, userId);
        if (!Integer.valueOf(ONGOING).equals(record.getStatus())) {
            throw new BusinessException(409, "该考试已交卷，无法读取暂存答案");
        }

        Map<String, String> answers = new LinkedHashMap<>();
        for (StudentAnswer row : answerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, recordId))) {
            if (row.getAnswer() != null && !row.getAnswer().isEmpty()) {
                answers.put(String.valueOf(row.getQuestionId()), row.getAnswer());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("answers", answers);
        return result;
    }

    /**
     * 到点自动结束：把过了 {@code end_time} 的「进行中」考试标记为「已结束」。
     *
     * <p>配合 {@code startExam} 的时间窗校验，{@code end_time} 才算真正生效：
     * 否则字段只是个摆设 —— 学生到点仍能开考，教师得手动去点「结束」。</p>
     *
     * <p>用条件更新（{@code WHERE status = 进行中}）而不是直接 set：
     * 并发下教师可能刚好手动结束了同一场考试。</p>
     *
     * @return 本次自动结束的考试数
     */
    @Scheduled(fixedDelayString = "${app.exam.auto-end-sweep-interval-ms:60000}")
    @Transactional
    public int autoEndExpiredExams() {
        List<Exam> expired = examMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Exam>()
                        .eq(Exam::getStatus, com.exam.common.constant.ExamStatus.IN_PROGRESS)
                        .isNotNull(Exam::getEndTime)
                        .lt(Exam::getEndTime, LocalDateTime.now()));
        int ended = 0;
        for (Exam exam : expired) {
            int changed = examMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Exam>()
                            .eq(Exam::getId, exam.getId())
                            .eq(Exam::getStatus, com.exam.common.constant.ExamStatus.IN_PROGRESS)
                            .set(Exam::getStatus, com.exam.common.constant.ExamStatus.FINISHED));
            if (changed > 0) {
                ended++;
            }
        }
        if (ended > 0) {
            log.info("到点自动结束：{} 场考试已结束", ended);
        }
        return ended;
    }

    /**
     * 投递判分任务。
     *
     * <p>发送失败只记日志：答卷已经落库，会由
     * {@link #sweepStuckPendingGradeRecords()} 兜底重判。
     * 在这里抛异常只会把一次已经成功的交卷变成 500（而数据其实已经写好了）。</p>
     */
    private void sendGradingTask(Map<String, Object> task) {
        try {
            rabbitTemplate.convertAndSend("exam.grading.exchange", "exam.grading", task);
        } catch (Exception e) {
            // 投递失败就地同步判分，而不是只等补偿任务：
            // 补偿任务的判据是「end_time 早于 120 秒」，也就是学生要等 2 分钟才看得到
            // 客观题成绩 —— 对「关掉 MQ 的单机部署」这是常态而非异常（broker 根本不存在），
            // 不能每次都让用户体验这个延迟。判分幂等，重复触发无副作用。
            Object recordId = task.get("recordId");
            log.error("判分消息投递失败，改为就地同步判分: task={}", task, e);
            if (recordId instanceof Number) {
                try {
                    gradingEngine.grade(((Number) recordId).longValue());
                } catch (Exception e2) {
                    log.error("就地同步判分也失败了，留给「待判分补偿任务」重判: recordId={}", recordId, e2);
                }
            }
        }
    }

    /**
     * 兜底：把长时间停在「待判分」的答卷重新判一遍。
     *
     * <p>覆盖三类情况：判分消息在事务提交前被消费导致幂等跳过、broker 抖动丢消息、
     * 消费者反复失败把消息送进了死信队列。判分本身幂等
     * （{@link GradingEngine#grade} 只处理「待判分」状态），重复触发没有副作用。</p>
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：一次扫描要处理多条记录，
     * 若共用一个大事务，其中一条判分失败会把整个事务标记为 rollback-only，
     * 其余本来能成功的记录也会一起回滚（并在提交时抛 UnexpectedRollbackException）。
     * 不开启外层事务时，每次 {@code grade()} 各自独立提交。</p>
     *
     * @return 本次重新判分的记录条数
     */
    @Scheduled(fixedDelayString = "${app.exam.pending-grade-sweep-interval-ms:120000}")
    public int sweepStuckPendingGradeRecords() {
        List<Long> stuck = recordMapper.selectStuckPendingGradeIds(PENDING_GRADE_STUCK_SECONDS);
        if (stuck == null || stuck.isEmpty()) {
            return 0;
        }
        int regraded = 0;
        for (Long id : stuck) {
            try {
                gradingEngine.grade(id);
                regraded++;
            } catch (Exception e) {
                // 单条失败不影响其余记录：下一轮扫描还会再试
                log.error("补偿判分失败 recordId={}: {}", id, e.getMessage());
            }
        }
        log.warn("补偿判分：{} 条长期停在「待判分」的答卷已重判", regraded);
        return regraded;
    }

    /**
     * 查询成绩
     */
    public ExamResultVO getResult(Long recordId, Long userId) {
        ExamRecord record = validateRecordAccess(recordId, userId);
        // 只有「考试中」才拒绝：还没交卷当然没有成绩。
        // 「待判分」允许查看，返回的是「已知部分的合计」并在响应里标出待复核题数。
        //   原先这里要求 status >= GRADED，而含主观题的答卷在教师复核前一直是「待判分」，
        //   偏偏又没有任何复核接口 —— 结果学生永远打不开成绩单。
        //   即便 AI 未启用，也应该能看到客观题成绩与「N 题待复核」，而不是一句冷冰冰的报错。
        if (record.getStatus() < PENDING_GRADE) {
            throw new BusinessException("考试尚未交卷，暂时没有成绩");
        }

        Exam exam = examMapper.selectById(record.getExamId());
        List<StudentAnswer> answers = answerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                        .eq(StudentAnswer::getRecordId, recordId));

        // 每题卷面满分取自试卷快照，与判分口径保持一致
        Map<Long, Integer> fullScoreMap = examRuleService.parseSnapshot(exam.getQuestionSnapshot()).stream()
                .filter(s -> s.getQuestionId() != null && s.getScore() != null)
                .collect(Collectors.toMap(com.exam.dto.QuestionSnapshot::getQuestionId,
                        com.exam.dto.QuestionSnapshot::getScore, (a, b) -> a));

        ExamResultVO vo = new ExamResultVO();
        vo.setRecordId(recordId);
        vo.setExamId(record.getExamId());
        vo.setExamTitle(exam.getTitle());
        vo.setTotalScore(exam.getTotalScore());
        vo.setScore(record.getScore());
        // 只把「判对」计入答对题数；部分正确（主观题拿部分分）不计入
        vo.setCorrectCount((int) answers.stream()
                .filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == CORRECT)
                .count());
        // 尚未评分的题数（主观题且 AI 未启用或失败）——前端据此显示「待复核」而不是「答错」
        int pendingReviewCount = (int) answers.stream()
                .filter(a -> a.getGradedBy() == null)
                .count();
        vo.setPendingReviewCount(pendingReviewCount);
        vo.setTotalCount(answers.size());
        // 只要还有题没评上，总分就只是「已知部分的合计」，此时不能给出及格判定——
        // 否则一份主观题答得很好、只是还没评的卷子会先被判成「未及格」。
        vo.setPassed(pendingReviewCount == 0
                && record.getScore() != null && exam.getPassScore() != null
                && record.getScore() >= exam.getPassScore());

        List<ExamResultVO.AnswerItem> items = new ArrayList<>();
        // 一次批量把本卷题目查成 Map 再在内存里取用。
        // 原先在循环里逐题 selectById：一份 50 题的卷子是 50 次数据库往返（N+1），
        // 成绩单是学生交卷后必点的页面，这个放大最不划算。
        // 题目按 id 批量查，但答案与返回顺序仍由 answers 决定，展示口径不变。
        Set<Long> questionIds = answers.stream()
                .map(StudentAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Question> questionMap = questionIds.isEmpty()
                ? Map.of()
                // 必须传 List：QuestionMapper 自带的 selectBatchIds(List<Long>) 与 BaseMapper 的
                // selectBatchIds(Collection) 同名但 statement 的 SQL 只认 @Param("ids")，
                // 传 Set 会解析到 Collection 重载上并抛 BindingException。
                : questionMapper.selectBatchIds(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        for (StudentAnswer sa : answers) {
            Question q = questionMap.get(sa.getQuestionId());
            if (q != null) {
                Integer full = fullScoreMap.getOrDefault(sa.getQuestionId(), q.getScore());
                items.add(ExamResultVO.AnswerItem.from(sa, q, full));
            }
        }
        vo.setAnswers(items);
        return vo;
    }

    /**
     * 考试统计
     */
    public ExamStatsVO getStats(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }

        com.exam.dto.ExamRecordStats stats = recordMapper.statsByExamId(examId);
        if (stats == null) {
            stats = new com.exam.dto.ExamRecordStats();
        }
        int passCount = exam.getPassScore() != null
                ? recordMapper.countPassed(examId, exam.getPassScore()) : 0;

        ExamStatsVO vo = new ExamStatsVO();
        vo.setExamId(examId);
        vo.setExamTitle(exam.getTitle());
        vo.setTotalStudents(nvl(stats.getTotalCount()));
        vo.setSubmittedCount(nvl(stats.getSubmittedCount()));
        vo.setPassCount(passCount);

        int submitted = vo.getSubmittedCount();
        // 及格率的分母是「已交卷人数」而不是「报考人数」：
        // 未交卷的人既不算及格也不算不及格，混进分母会让及格率被稀释
        vo.setPassRate(submitted > 0 ? (double) passCount / submitted * 100 : 0);
        vo.setAvgScore(stats.getAvgScore() == null ? 0 : stats.getAvgScore());
        vo.setMaxScore(nvl(stats.getMaxScore()));
        vo.setMinScore(nvl(stats.getMinScore()));
        vo.setDistribution(buildScoreDistribution(examId, exam.getTotalScore()));

        return vo;
    }

    /** 得分率分段的段下界（%）。最后一段取到 100% 及以上 */
    private static final int[] DISTRIBUTION_BOUNDS = {0, 60, 70, 80, 90};

    /**
     * 分数分布：按得分率分成 5 段（{@code <60 / 60-69 / 70-79 / 80-89 / ≥90}）。
     *
     * <p>只统计「有分」的记录：<b>考试中</b>的记录 score 为 null，
     * <b>超时</b>的记录按设计只改状态、不伪造分数（见 {@code sweepTimeoutRecords}），
     * 两者都不该进分布 —— 混进去会让「不及格」凭空多出一截。</p>
     */
    private List<ExamStatsVO.ScoreBucket> buildScoreDistribution(Long examId, Integer totalScore) {
        int total = (totalScore == null || totalScore <= 0) ? 100 : totalScore;
        List<ExamRecord> scored = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                        .select(ExamRecord::getScore)
                        .eq(ExamRecord::getExamId, examId)
                        .isNotNull(ExamRecord::getScore));

        int[] counts = new int[DISTRIBUTION_BOUNDS.length];
        for (ExamRecord r : scored) {
            double rate = r.getScore() * 100.0 / total;
            int last = DISTRIBUTION_BOUNDS.length - 1;
            int idx = last;
            for (int i = 0; i < last; i++) {
                if (rate >= DISTRIBUTION_BOUNDS[i] && rate < DISTRIBUTION_BOUNDS[i + 1]) {
                    idx = i;
                    break;
                }
            }
            counts[idx]++;
        }

        List<ExamStatsVO.ScoreBucket> out = new ArrayList<>(DISTRIBUTION_BOUNDS.length);
        int last = DISTRIBUTION_BOUNDS.length - 1;
        for (int i = 0; i < DISTRIBUTION_BOUNDS.length; i++) {
            String label = (i == last)
                    ? DISTRIBUTION_BOUNDS[i] + "-100%"
                    : DISTRIBUTION_BOUNDS[i] + "-" + (DISTRIBUTION_BOUNDS[i + 1] - 1) + "%";
            out.add(new ExamStatsVO.ScoreBucket(label, counts[i], DISTRIBUTION_BOUNDS[i] >= 60));
        }
        return out;
    }

    /** null 安全的取值，避免聚合查询在无人交卷时返回 null 导致拆箱空指针 */
    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 查询学生的考试记录（含试卷标题、总分、及格分等展示字段）。
     *
     * <p>用一次 {@code selectBatchIds} 把涉及的试卷全部取出再在内存里拼装，
     * 避免每条记录各查一次试卷（N+1 查询）。</p>
     */
    public List<MyRecordVO> getMyRecords(Long userId) {
        List<ExamRecord> records = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getUserId, userId)
                        .orderByDesc(ExamRecord::getCreatedAt));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> examIds = records.stream()
                .map(ExamRecord::getExamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Exam> examMap = examIds.isEmpty()
                ? Collections.emptyMap()
                : examMapper.selectBatchIds(examIds).stream()
                    .collect(Collectors.toMap(Exam::getId, e -> e, (a, b) -> a));

        List<MyRecordVO> list = new ArrayList<>(records.size());
        for (ExamRecord r : records) {
            Exam exam = examMap.get(r.getExamId());
            MyRecordVO vo = new MyRecordVO();
            vo.setRecordId(r.getId());
            vo.setExamId(r.getExamId());
            vo.setExamTitle(exam == null ? "（试卷已删除）" : exam.getTitle());
            vo.setScore(r.getScore());
            vo.setStartTime(r.getStartTime());
            vo.setEndTime(r.getEndTime());
            vo.setStatus(r.getStatus());
            vo.setStatusText(statusText(r.getStatus()));
            if (exam != null) {
                vo.setTotalScore(exam.getTotalScore());
                vo.setPassScore(exam.getPassScore());
                vo.setDurationMinutes(exam.getDuration());
                // 截止时刻 = 开考时间 + 时长。转成 epoch 毫秒下发，
                // 避免前端解析 "yyyy-MM-dd HH:mm:ss" 字符串时的时区歧义。
                if (r.getStartTime() != null && exam.getDuration() != null) {
                    vo.setDeadlineMillis(r.getStartTime()
                            .plusMinutes(exam.getDuration())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli());
                }
                // 未出分（考试中/待判分/超时）时 passed 留 null，前端显示「—」而不是「不及格」
                if (r.getScore() != null && exam.getPassScore() != null
                        && r.getStatus() != null && r.getStatus() >= GRADED) {
                    vo.setPassed(r.getScore() >= exam.getPassScore());
                }
            }
            list.add(vo);
        }
        return list;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ONGOING -> "考试中";
            case PENDING_GRADE -> "待判分";
            case GRADED -> "已出分";
            case TIMEOUT -> "超时";
            default -> "未知";
        };
    }

    // ==================== 考试时限 ====================

    /**
     * 校验是否仍在考试时限内，超时则拒绝交卷。
     *
     * <p><b>为什么必须有这一步</b>：倒计时此前只存在于前端展示与 WebSocket 推送里，
     * 服务端交卷时从不看时间。结果是学生开考后挂机任意久都能交卷，
     * 且照常判分——考试的时间限制完全靠前端自觉，形同虚设。</p>
     *
     * <p>截止时刻 = {@code start_time + duration}，与 {@code getMyRecords}
     * 下发给前端的 {@code deadlineMillis} 用同一口径，两端不会打架。</p>
     */
    private void assertWithinDeadline(ExamRecord record, Exam exam) {
        if (record.getStartTime() == null || exam.getDuration() == null) {
            // 数据不完整时不拦截：宁可放过，也不要因为脏数据把正常考生挡在门外
            log.warn("考试记录缺少 start_time 或 duration，跳过时限校验 recordId={}", record.getId());
            return;
        }
        LocalDateTime deadline = record.getStartTime().plusMinutes(exam.getDuration());
        if (LocalDateTime.now().isAfter(deadline.plusSeconds(SUBMIT_GRACE_SECONDS))) {
            log.warn("拒绝超时交卷 recordId={}, userId={}, deadline={}",
                    record.getId(), record.getUserId(), deadline);
            throw new BusinessException(409, "考试已超时，无法交卷");
        }
    }

    /**
     * 定时清理超时未交卷的记录。
     *
     * <p><b>没有这个任务，{@code RecordStatus.TIMEOUT} 就是一个永远写不进数据库的死常量</b>，
     * 而「开了考再也没回来」的记录会永远停在「考试中」：
     * 学生的成绩列表里一直挂着一场没结束的考试，教师统计也会把它算作未完成。</p>
     *
     * <p>只改状态、不计分：学生根本没有交卷，答案从未落库，
     * 没有东西可判。分数保持 null，前端会显示「—」而不是「0 分」。</p>
     *
     * <p>用 CAS（{@code WHERE status = ONGOING}）而不是直接 UPDATE：
     * 扫到一半时学生恰好交卷，两次写入不能互相覆盖。</p>
     *
     * @return 本次标记为超时的记录条数
     */
    @Scheduled(fixedDelayString = "${app.exam.timeout-sweep-interval-ms:60000}")
    @Transactional
    public int sweepTimeoutRecords() {
        List<Long> expiredIds = recordMapper.selectExpiredOngoingIds(SUBMIT_GRACE_SECONDS);
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        int swept = 0;
        for (Long id : expiredIds) {
            if (recordMapper.compareAndSetStatus(id, ONGOING, TIMEOUT) > 0) {
                swept++;
                // 超时未交卷 → 连同暂存的作答一起删掉。
                // 「超时」的语义是「学生没有提交」；留下暂存行会让成绩单显示出
                // 他其实写过的内容、而分数又是空的，自相矛盾。
                // 这也与「超时只改状态、不伪造交卷时间与分数」的既定口径一致。
                answerMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAnswer>()
                                .eq(StudentAnswer::getRecordId, id));
                // 顺带清掉 Redis 倒计时 key，避免残留到 TTL 自然过期
                if (redisTemplate != null) {
                    try {
                        ExamRecord r = recordMapper.selectById(id);
                        if (r != null) {
                            redisTemplate.delete(String.format(EXAM_TIMEOUT_KEY, r.getExamId(), r.getUserId()));
                        }
                    } catch (Exception e) {
                        log.warn("清理超时记录的 Redis 倒计时 key 失败 recordId={}: {}", id, e.getMessage());
                    }
                }
            }
        }
        if (swept > 0) {
            log.info("定时清理：{} 条超时未交卷记录已标记为超时", swept);
        }
        return swept;
    }

    private ExamRecord validateRecordAccess(Long recordId, Long userId) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(404, "考试记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能查看自己的考试记录");
        }
        return record;
    }
}