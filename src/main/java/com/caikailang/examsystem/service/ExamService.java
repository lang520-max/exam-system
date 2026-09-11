package com.caikailang.examsystem.service;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.domain.ExamAnswer;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.ExamPaperQuestion;
import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.ExamQuestionView;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.ExamStats;
import com.caikailang.examsystem.dto.GradeItem;
import com.caikailang.examsystem.dto.GradeResult;
import com.caikailang.examsystem.dto.SubmitForm;
import com.caikailang.examsystem.mapper.ExamAnswerMapper;
import com.caikailang.examsystem.mapper.ExamPaperMapper;
import com.caikailang.examsystem.mapper.ExamPaperQuestionMapper;
import com.caikailang.examsystem.mapper.ExamRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 考试服务：开考、答题、交卷判分、成绩与统计。
 *
 * <p>本类集中体现三个工程要点：</p>
 * <ol>
 *   <li><b>身份只认 Session</b>：所有方法都以调用方传入的 {@link User}（来自 Session）为准，
 *       任何前端传的 studentId / userId 都不参与鉴权；</li>
 *   <li><b>防重复交卷</b>：交卷走条件更新 {@code WHERE status = 'ONGOING'}，
 *       受影响行数为 0 直接拒绝，绝不重复写分数；配合
 *       {@code UNIQUE(paper_id, student_id)} 与 {@code UNIQUE(record_id, question_id)} 两道唯一约束兜底；</li>
 *   <li><b>判分与写库解耦</b>：判分交给无状态的 {@link GradeCalculator}，
 *       本类只负责取题、调用判分、把结果落库。</li>
 * </ol>
 *
 * @author 蔡开浪
 */
@Service
public class ExamService {

    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamAnswerMapper answerMapper;
    private final GradeCalculator gradeCalculator;

    public ExamService(ExamPaperMapper paperMapper,
                       ExamPaperQuestionMapper paperQuestionMapper,
                       ExamRecordMapper recordMapper,
                       ExamAnswerMapper answerMapper,
                       GradeCalculator gradeCalculator) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.gradeCalculator = gradeCalculator;
    }

    /**
     * 开始考试：生成考试记录并记录开始时间。
     *
     * <p>幂等处理：同一学生同一试卷只有一条记录。若已交卷则拒绝重复开考；
     * 若还在答题中（例如中途断线）则返回原记录，让学生接着答，
     * 而不是重新计时——否则刷新页面就能刷新考试时长。</p>
     */
    @Transactional
    public ExamRecord startExam(Long paperId, User student) {
        if (student == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        if (!student.isStudent()) {
            throw new BusinessException(403, "只有学生可以参加考试");
        }
        ExamPaper paper = paperMapper.findById(paperId);
        if (paper == null) {
            throw new BusinessException(404, "试卷不存在或已被删除");
        }
        if (!paper.isPublished()) {
            throw new BusinessException("该考试尚未发布或已结束");
        }

        ExamRecord existing = recordMapper.findByPaperAndStudent(paperId, student.getId());
        if (existing != null) {
            if (existing.isSubmitted()) {
                throw new BusinessException("你已经参加过这场考试，不能重复开考");
            }
            return existing;
        }

        ExamRecord record = new ExamRecord();
        record.setPaperId(paperId);
        record.setStudentId(student.getId());
        record.setStartTime(new Date());
        record.setScore(BigDecimal.ZERO);
        record.setCorrectCount(0);
        record.setTotalCount(paperQuestionMapper.countByPaperId(paperId));
        record.setStatus(ExamRecord.STATUS_ONGOING);
        record.setTimeout(Boolean.FALSE);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发双击「开始考试」：唯一约束 (paper_id, student_id) 只放行一条插入，
            // 另一条会撞约束。这里回读已存在的那条返回，把数据库异常转成正常业务流程。
            ExamRecord concurrent = recordMapper.findByPaperAndStudent(paperId, student.getId());
            if (concurrent == null) {
                throw e;
            }
            return concurrent;
        }
        return recordMapper.findById(record.getId());
    }

    /**
     * 取考试题目（不含正确答案）。
     *
     * @throws BusinessException 记录不存在、非本人、或已交卷时抛出
     */
    public List<ExamQuestionView> getExamQuestions(Long recordId, User operator) {
        ExamRecord record = requireRecordAccess(recordId, operator);
        if (!record.isOngoing()) {
            throw new BusinessException("该试卷已交卷，不能继续答题");
        }
        return ExamQuestionView.from(paperQuestionMapper.findByPaperId(record.getPaperId()));
    }

    /** 考试记录详情（含试卷标题、及格分、截止时间等展示字段） */
    public ExamRecord detail(Long recordId, User operator) {
        return requireRecordAccess(recordId, operator);
    }

    /**
     * 交卷并自动判分。
     *
     * <p>流程：校验归属 → 取卷面题目（分值用试卷快照）→ 纯函数判分 →
     * <b>条件更新抢占交卷权</b> → 批量写入作答明细。</p>
     *
     * <p>超过 {@code 开始时间 + 考试时长} 交卷不拒绝，但会把记录标记为超时，
     * 这样学生不会因为网络卡顿丢掉整张卷子，教师也能在成绩单上看到超时标记。</p>
     */
    @Transactional
    public ExamRecord submit(SubmitForm form, User student) {
        if (student == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        if (!student.isStudent()) {
            throw new BusinessException(403, "只有学生可以交卷");
        }
        ExamRecord record = recordMapper.findById(form.getRecordId());
        if (record == null) {
            throw new BusinessException(404, "考试记录不存在");
        }
        if (!record.getStudentId().equals(student.getId())) {
            throw new BusinessException(403, "只能提交自己的答卷");
        }
        if (!record.isOngoing()) {
            throw new BusinessException("该试卷已交卷，请勿重复提交");
        }

        List<ExamPaperQuestion> paperQuestions = paperQuestionMapper.findByPaperId(record.getPaperId());
        if (paperQuestions.isEmpty()) {
            throw new BusinessException("试卷没有题目，无法判分");
        }
        List<Question> questions = new ArrayList<Question>(paperQuestions.size());
        for (ExamPaperQuestion pq : paperQuestions) {
            questions.add(pq.toQuestion());
        }

        // 判分：纯函数，不碰数据库
        GradeResult result = gradeCalculator.grade(questions, form.getAnswers());

        Date now = new Date();
        Date deadline = record.getDeadline();
        boolean timeout = deadline != null && now.after(deadline);

        ExamRecord toSubmit = new ExamRecord();
        toSubmit.setId(record.getId());
        toSubmit.setScore(result.getTotalScore());
        toSubmit.setCorrectCount(result.getCorrectCount());
        toSubmit.setTotalCount(result.getTotalCount());
        toSubmit.setTimeout(Boolean.valueOf(timeout));

        // 条件更新：只有仍处于 ONGOING 才能被改成 SUBMITTED。
        // 并发的两次交卷请求（例如用户连点两次、或前端定时器与手动点击同时触发）
        // 只有一次能把状态改掉，另一次的受影响行数为 0，直接拒绝，绝不会重复计分。
        int changed = recordMapper.compareAndSetSubmit(toSubmit);
        if (changed == 0) {
            throw new BusinessException("该试卷已交卷，请勿重复提交");
        }

        List<ExamAnswer> answers = new ArrayList<ExamAnswer>(result.getItems().size());
        for (GradeItem item : result.getItems()) {
            ExamAnswer answer = new ExamAnswer();
            answer.setRecordId(record.getId());
            answer.setQuestionId(item.getQuestionId());
            answer.setStudentAnswer(item.getStudentAnswer());
            answer.setCorrectAnswer(item.getCorrectAnswer());
            answer.setScore(item.getFullScore());
            answer.setEarnedScore(item.getEarnedScore());
            answer.setCorrect(Boolean.valueOf(item.isCorrect()));
            answers.add(answer);
        }
        // 未作答的题也会写入一行（得 0 分），保证成绩单题目数与试卷一致
        answerMapper.batchInsert(answers);

        return recordMapper.findById(record.getId());
    }

    /** 学生查看自己的考试记录（studentId 强制取 Session 身份，忽略前端传值） */
    public PageResult<ExamRecord> myRecords(ExamRecordQuery query, User student) {
        if (student == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        query.setStudentId(student.getId());
        return pageRecords(query);
    }

    /** 教师查看某场考试的成绩列表 */
    public PageResult<ExamRecord> paperRecords(ExamRecordQuery query, User operator) {
        QuestionService.requireTeacher(operator);
        if (query.getPaperId() == null) {
            throw new BusinessException("请先指定要查看的试卷");
        }
        return pageRecords(query);
    }

    /** 某场考试的成绩统计：平均分、及格率、最高/最低分 */
    public ExamStats paperStats(Long paperId, User operator) {
        QuestionService.requireTeacher(operator);
        ExamPaper paper = paperMapper.findById(paperId);
        if (paper == null) {
            throw new BusinessException(404, "试卷不存在或已被删除");
        }
        ExamStats stats = recordMapper.statsByPaper(paperId, paper.getPassScore());
        if (stats == null) {
            stats = new ExamStats();
        }
        stats.setPaperId(paperId);
        stats.setPaperTitle(paper.getTitle());
        stats.setPassScore(paper.getPassScore());
        return stats;
    }

    /** 学生个人成绩概览：考了几场、平均分、最高分、及格门数 */
    public ExamStats myStats(User student) {
        if (student == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        ExamStats stats = recordMapper.statsByStudent(student.getId());
        return stats == null ? new ExamStats() : stats;
    }

    /** 答卷详情（交卷后的成绩单，含每题的作答与正确答案） */
    public List<ExamAnswer> review(Long recordId, User operator) {
        ExamRecord record = requireRecordAccess(recordId, operator);
        if (!record.isSubmitted()) {
            throw new BusinessException("尚未交卷，暂时没有成绩与答题回顾");
        }
        return answerMapper.findByRecordId(recordId);
    }

    /** 错题回顾：只返回判错的题 */
    public List<ExamAnswer> wrongAnswers(Long recordId, User operator) {
        ExamRecord record = requireRecordAccess(recordId, operator);
        if (!record.isSubmitted()) {
            throw new BusinessException("尚未交卷，暂时没有错题回顾");
        }
        return answerMapper.findWrongByRecordId(recordId);
    }

    private PageResult<ExamRecord> pageRecords(ExamRecordQuery query) {
        long total = recordMapper.countByQuery(query);
        List<ExamRecord> list = total == 0
            ? Collections.<ExamRecord>emptyList()
            : recordMapper.findByQuery(query);
        return new PageResult<ExamRecord>(list, total, query.getPageNum(), query.getSafePageSize());
    }

    /**
     * 取考试记录并校验访问权限。
     *
     * <p>学生只能访问自己的记录；教师可以查看任意记录（用于成绩复核）。</p>
     */
    private ExamRecord requireRecordAccess(Long recordId, User operator) {
        if (operator == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        ExamRecord record = recordMapper.findById(recordId);
        if (record == null) {
            throw new BusinessException(404, "考试记录不存在");
        }
        if (!record.getStudentId().equals(operator.getId()) && !operator.isTeacher()) {
            throw new BusinessException(403, "只能查看自己的考试成绩");
        }
        return record;
    }
}
