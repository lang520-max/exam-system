package com.exam.service;

import com.exam.common.constant.Correctness;
import com.exam.common.exception.BusinessException;
import com.exam.dto.QuestionSnapshot;
import com.exam.dto.request.ExamReviewDTO;
import com.exam.dto.response.ExamReviewVO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.entity.Question;
import com.exam.entity.StudentAnswer;
import com.exam.entity.User;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.StudentAnswerMapper;
import com.exam.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.exam.common.constant.RecordStatus.GRADED;
import static com.exam.common.constant.RecordStatus.ONGOING;
import static com.exam.common.constant.RecordStatus.PENDING_GRADE;

/**
 * 教师批改服务。
 *
 * <p><b>为什么需要它</b>：在此之前，主观题的「待复核」是一条走不通的路——
 * {@code GradingEngine.regrade()} 与 {@code reviewedBy/reviewedAt} 都是死代码，
 * 没有任何接口能让教师真正改分。接入 AI 评分后这个问题更明显：
 * AI 直接定分意味着<b>判错也没有人能改</b>。这里把另一半补上。</p>
 *
 * <p>教师可以改<b>任何</b>一题的分数，包括客观题与已经由 AI 打过分的主观题：
 * 教师是评分的最终权威，AI 只是提速手段。</p>
 *
 * <p>改分后统一重算该考生这份卷子的总分与状态：还有题没评就停在「待判分」，
 * 全部评完才置为「已出分」。</p>
 */
@Slf4j
@Service
public class ExamReviewService {

    private final ExamMapper examMapper;
    private final ExamRecordMapper recordMapper;
    private final StudentAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final ExamRuleService examRuleService;

    public ExamReviewService(ExamMapper examMapper,
                             ExamRecordMapper recordMapper,
                             StudentAnswerMapper answerMapper,
                             QuestionMapper questionMapper,
                             UserMapper userMapper,
                             ExamRuleService examRuleService) {
        this.examMapper = examMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
        this.examRuleService = examRuleService;
    }

    /**
     * 批改视图：一场考试下所有考生的逐题作答。
     *
     * <p>不做分页：这是给教师在小规模班级里用的批改界面，
     * 一场考试几十份卷子一次返回完全够用，加了分页反而让「跨学生找待批改的题」变麻烦。
     * 如果将来要用于全校规模的考试，这里需要改成按考生分页。</p>
     */
    public ExamReviewVO getReviewSheet(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        Map<Long, Integer> fullScores = snapshotScores(exam);

        List<ExamRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .orderByAsc(ExamRecord::getId));

        Map<Long, String> studentNames = loadStudentNames(records);

        // 全班作答与题目各查一次，再在内存里按考生分组。
        // 原先是「每个考生一次 selectList + 每道题一次 selectById」：
        // 一场 50 人、每人 20 题的考试就是 1000+ 次数据库往返，批改页必然卡住。
        List<StudentAnswer> allAnswers = answersOfRecords(records);
        Map<Long, List<StudentAnswer>> answersByRecord = new LinkedHashMap<>();
        for (StudentAnswer sa : allAnswers) {
            answersByRecord.computeIfAbsent(sa.getRecordId(), id -> new ArrayList<>()).add(sa);
        }
        Map<Long, Question> questions = loadQuestionsByIds(allAnswers.stream()
                .map(StudentAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        ExamReviewVO vo = new ExamReviewVO();
        vo.setExamId(examId);
        vo.setExamTitle(exam.getTitle());
        vo.setTotalScore(exam.getTotalScore());
        vo.setPassScore(exam.getPassScore());

        int pendingTotal = 0;
        List<ExamReviewVO.StudentRecord> rows = new ArrayList<>(records.size());

        for (ExamRecord record : records) {
            List<StudentAnswer> answers = answersByRecord.getOrDefault(record.getId(), List.of());
            int pending = 0;

            List<ExamReviewVO.AnswerItem> items = new ArrayList<>(answers.size());
            for (StudentAnswer sa : answers) {
                Question q = questions.get(sa.getQuestionId());
                if (q == null) {
                    continue;
                }
                ExamReviewVO.AnswerItem item = new ExamReviewVO.AnswerItem();
                item.setAnswerId(sa.getId());
                item.setQuestionId(sa.getQuestionId());
                item.setType(q.getType());
                item.setQuestionContent(q.getContent());
                item.setCorrectAnswer(q.getAnswer());
                item.setStudentAnswer(sa.getAnswer());
                item.setFullScore(fullScores.getOrDefault(sa.getQuestionId(),
                        q.getScore() == null ? 0 : q.getScore()));
                item.setEarnedScore(sa.getScore());
                item.setGradedBy(sa.getGradedBy());
                item.setAiReason(sa.getAiReason());
                boolean isPending = sa.getGradedBy() == null;
                item.setPendingReview(isPending);
                if (isPending) {
                    pending++;
                }
                items.add(item);
            }

            ExamReviewVO.StudentRecord row = new ExamReviewVO.StudentRecord();
            row.setRecordId(record.getId());
            row.setUserId(record.getUserId());
            row.setStudentName(studentNames.getOrDefault(record.getUserId(), "（未知考生）"));
            row.setStatus(record.getStatus());
            row.setStatusText(statusText(record.getStatus()));
            row.setScore(record.getScore());
            row.setPendingCount(pending);
            row.setAnswers(items);
            rows.add(row);

            pendingTotal += pending;
        }

        vo.setRecords(rows);
        vo.setPendingTotal(pendingTotal);
        return vo;
    }

    /**
     * 提交批改结果（不区分身份）。
     *
     * <p>保留这个两参重载，既有调用方与测试不受影响；控制器请用带
     * {@code isAdmin} 的那个 —— 只有管理员可以批改其他教师的考试。</p>
     */
    @Transactional
    public Map<String, Object> reviewAnswers(ExamReviewDTO form, Long teacherId) {
        return reviewAnswers(form, teacherId, false);
    }

    /**
     * 提交批改结果。
     *
     * @param form      待保存的分数
     * @param teacherId 复核人（写入 reviewed_by）
     * @param isAdmin   管理员放行归属校验
     * @return 受影响的记录及其重算后的总分
     */
    @Transactional
    public Map<String, Object> reviewAnswers(ExamReviewDTO form, Long teacherId, boolean isAdmin) {
        Set<Long> affectedRecords = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        // 按考试缓存快照分值：否则每道题都要把同一份 snapshot JSON 重新解析一遍
        Map<Long, Map<Long, Integer>> snapshotCache = new LinkedHashMap<>();
        Map<Long, Exam> examCache = new LinkedHashMap<>();
        int updated = 0;

        // 先把这批批改涉及的作答 / 记录 / 题目各批量查一次：原先在循环里逐项 selectById，
        // 一次提交几十题就是上百次往返。
        // 循环本身仍然「按提交顺序逐项处理」—— skipped 的提示顺序、同一答案被重复提交时的
        // 处理次数都依赖这个顺序，所以只把查询换成内存查表，判定逻辑一行没动。
        Map<Long, StudentAnswer> answersById = loadAnswersByIds(form.getItems().stream()
                .map(ExamReviewDTO.Item::getAnswerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, ExamRecord> recordsById = loadRecordsByIds(answersById.values().stream()
                .map(StudentAnswer::getRecordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, Question> questionsById = loadQuestionsByIds(answersById.values().stream()
                .map(StudentAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        for (ExamReviewDTO.Item item : form.getItems()) {
            Long answerId = item.getAnswerId();
            StudentAnswer answer = answerId == null ? null : answersById.get(answerId);
            if (answer == null) {
                skipped.add("答案 " + item.getAnswerId() + " 不存在");
                continue;
            }
            ExamRecord record = recordsById.get(answer.getRecordId());
            if (record == null) {
                skipped.add("答案 " + item.getAnswerId() + " 对应的考试记录不存在");
                continue;
            }
            if (Objects.equals(record.getStatus(), ONGOING)) {
                // 学生还在答题，此时改分会把记录提前置为「已出分」，
                // 反而让学生交不了卷。直接跳过并如实告知。
                skipped.add("考生 " + record.getUserId() + " 尚未交卷，不能批改");
                continue;
            }
            Question question = questionsById.get(answer.getQuestionId());
            if (question == null) {
                skipped.add("答案 " + item.getAnswerId() + " 对应的题目已删除");
                continue;
            }

            Exam exam = examCache.computeIfAbsent(record.getExamId(),
                    id -> examMapper.selectById(id));

            // 归属校验（M4）：教师只能批改自己创建的考试，管理员放行。
            // 检查放在这里而不是方法入口：入口拿不到「这场考试是谁的」，
            // 必须先由 answerId 反查出记录与试卷。方法带 @Transactional，
            // 抛异常会整批回滚，不会留下「批了一半」的中间状态。
            if (!isAdmin && (exam == null || exam.getCreatedBy() == null
                    || !exam.getCreatedBy().equals(teacherId))) {
                throw new BusinessException(403, "只能批改自己创建的考试");
            }

            Map<Long, Integer> fullScores = snapshotCache.computeIfAbsent(record.getExamId(),
                    id -> snapshotScores(exam));
            Integer fromSnapshot = fullScores.get(answer.getQuestionId());
            int fullScore = fromSnapshot != null
                    ? fromSnapshot
                    : (question.getScore() == null ? 0 : question.getScore());

            int score = clamp(item.getScore(), fullScore);
            if (score != item.getScore()) {
                log.info("批改分数被裁剪: answerId={}, 提交={}, 实际={}（本题满分 {}）",
                        answer.getId(), item.getScore(), score, fullScore);
            }
            int correctness = score >= fullScore && fullScore > 0 ? Correctness.CORRECT
                    : score <= 0 ? Correctness.WRONG
                    : Correctness.PARTIAL;

            answerMapper.updateTeacherScore(answer.getId(), score, correctness, teacherId);
            affectedRecords.add(record.getId());
            updated++;
        }

        List<Map<String, Object>> recalculated = new ArrayList<>(affectedRecords.size());
        for (Long recordId : affectedRecords) {
            int total = answerMapper.sumScoreByRecordId(recordId);
            int pending = answerMapper.countPendingByRecordId(recordId);
            // 还有题没评就留在「待判分」，全部评完才出分
            int status = pending == 0 ? GRADED : PENDING_GRADE;
            recordMapper.updateScoreAndStatus(recordId, total, status);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", recordId);
            row.put("score", total);
            row.put("pendingCount", pending);
            row.put("status", status);
            recalculated.add(row);
        }

        log.info("教师批改完成: 更新 {} 题, 涉及 {} 份答卷, 跳过 {} 项",
                updated, affectedRecords.size(), skipped.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedCount", updated);
        result.put("records", recalculated);
        result.put("skipped", skipped);
        return result;
    }

    // ---------------------------------------------------------------- 内部

    /** 一次取回多份答卷的全部作答，按 id 升序 —— 与原先「逐份查询」的顺序口径一致 */
    private List<StudentAnswer> answersOfRecords(List<ExamRecord> records) {
        Set<Long> recordIds = records.stream()
                .map(ExamRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (recordIds.isEmpty()) {
            return List.of();
        }
        return answerMapper.selectList(
                new LambdaQueryWrapper<StudentAnswer>()
                        .in(StudentAnswer::getRecordId, recordIds)
                        .orderByAsc(StudentAnswer::getId));
    }

    /** 一次取回答作：answerId -> 作答 */
    private Map<Long, StudentAnswer> loadAnswersByIds(Set<Long> answerIds) {
        if (answerIds.isEmpty()) {
            return Map.of();
        }
        return answerMapper.selectBatchIds(answerIds).stream()
                .collect(Collectors.toMap(StudentAnswer::getId, a -> a, (a, b) -> a));
    }

    /** 一次取回考试记录：recordId -> 记录 */
    private Map<Long, ExamRecord> loadRecordsByIds(Set<Long> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        return recordMapper.selectBatchIds(recordIds).stream()
                .collect(Collectors.toMap(ExamRecord::getId, r -> r, (a, b) -> a));
    }

    /** 一次取回题目：questionId -> 题目 */
    private Map<Long, Question> loadQuestionsByIds(Set<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        // 必须转成 List 再传：QuestionMapper 自己声明了 selectBatchIds(List<Long>)，
        // 与 BaseMapper 的 selectBatchIds(Collection) 同名却共用同一个 statement id，
        // 而该名字下注册的 SQL 用的是 @Param("ids")。传 Set 会落到 Collection 重载上，
        // 参数名对不上，直接 BindingException。
        return questionMapper.selectBatchIds(new ArrayList<>(questionIds)).stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
    }

    /** 批量取学生姓名，避免逐个查询造成 N+1 */
    private Map<Long, String> loadStudentNames(List<ExamRecord> records) {
        Set<Long> userIds = records.stream()
                .map(ExamRecord::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getRealName() != null && !u.getRealName().isBlank()
                                ? u.getRealName() : u.getUsername(),
                        (a, b) -> a));
    }

    /** 试卷快照：questionId -> 卷面满分 */
    private Map<Long, Integer> snapshotScores(Exam exam) {
        if (exam == null) {
            return Collections.emptyMap();
        }
        return examRuleService.parseSnapshot(exam.getQuestionSnapshot()).stream()
                .filter(s -> s.getQuestionId() != null && s.getScore() != null)
                .collect(Collectors.toMap(QuestionSnapshot::getQuestionId,
                        QuestionSnapshot::getScore, (a, b) -> a));
    }

    private static int clamp(Integer raw, int fullScore) {
        int value = raw == null ? 0 : raw;
        return Math.max(0, Math.min(fullScore, value));
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ONGOING -> "考试中";
            case PENDING_GRADE -> "待判分";
            case GRADED -> "已出分";
            case 3 -> "超时";
            default -> "未知";
        };
    }
}