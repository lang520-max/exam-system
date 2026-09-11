package com.caikailang.examsystem.service;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.ExamPaperQuestion;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.PaperForm;
import com.caikailang.examsystem.dto.RandomPaperForm;
import com.caikailang.examsystem.mapper.ExamPaperMapper;
import com.caikailang.examsystem.mapper.ExamPaperQuestionMapper;
import com.caikailang.examsystem.mapper.ExamRecordMapper;
import com.caikailang.examsystem.mapper.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组卷服务：手动选题组卷 + 按规则随机抽题组卷。
 *
 * <p>两种组卷都会写两张表（{@code exam_paper} 与 {@code exam_paper_question}），
 * 因此都用 {@code @Transactional} 包起来：任何一步失败（例如题目不足、
 * 关联表写失败），试卷主记录不会被留下成为一张「没有题目的空卷」。</p>
 *
 * @author 蔡开浪
 */
@Service
public class ExamPaperService {

    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final ExamRecordMapper recordMapper;

    public ExamPaperService(ExamPaperMapper paperMapper,
                            ExamPaperQuestionMapper paperQuestionMapper,
                            QuestionMapper questionMapper,
                            ExamRecordMapper recordMapper) {
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.questionMapper = questionMapper;
        this.recordMapper = recordMapper;
    }

    /**
     * 手动组卷：教师勾选题目，按题库中的默认分值（或表单指定的覆盖分值）生成试卷。
     *
     * <p>生成的试卷是 {@code DRAFT} 草稿状态，教师确认无误后再发布，
     * 学生端只看得见 {@code PUBLISHED} 的试卷。</p>
     */
    @Transactional
    public ExamPaper createManual(PaperForm form, User operator) {
        QuestionService.requireTeacher(operator);

        // 同一份试卷里同一道题只能出现一次，否则会撞上 (paper_id, question_id) 唯一约束
        Set<Long> ids = new LinkedHashSet<Long>(form.getQuestionIds());
        if (ids.size() != form.getQuestionIds().size()) {
            throw new BusinessException("同一道题不能在一份试卷中重复出现");
        }

        List<Question> questions = questionMapper.findByIds(new ArrayList<Long>(ids));
        if (questions.size() != ids.size()) {
            throw new BusinessException(404, "部分题目不存在或已被删除，请刷新题库后重新选题");
        }
        // findByIds 按 id 升序返回，这里还原成教师勾选的顺序，题号与选择顺序一致
        Map<Long, Question> questionMap = new java.util.HashMap<Long, Question>();
        for (Question question : questions) {
            questionMap.put(question.getId(), question);
        }

        ExamPaper paper = new ExamPaper();
        paper.setTitle(form.getTitle().trim());
        paper.setSubject(form.getSubject().trim());
        paper.setDurationMinutes(form.getDurationMinutes());
        paper.setPassScore(form.getPassScore());
        paper.setStatus(ExamPaper.STATUS_DRAFT);
        paper.setCreatorId(operator.getId());

        List<ExamPaperQuestion> paperQuestions = new ArrayList<ExamPaperQuestion>();
        BigDecimal totalScore = BigDecimal.ZERO;
        int sortOrder = 1;
        for (Long questionId : ids) {
            Question question = questionMap.get(questionId);
            BigDecimal score = resolveScore(form, questionId, question);
            totalScore = totalScore.add(score);

            ExamPaperQuestion pq = new ExamPaperQuestion();
            pq.setQuestionId(questionId);
            pq.setScore(score);
            pq.setSortOrder(sortOrder++);
            paperQuestions.add(pq);
        }
        checkPassScore(form.getPassScore(), totalScore);
        paper.setTotalScore(totalScore);
        return savePaper(paper, paperQuestions);
    }

    /**
     * 随机组卷：按「科目 + 题型 + 数量 + 每题分值」从题库随机抽题。
     *
     * <p>抽题在数据库侧用 {@code ORDER BY RAND() LIMIT n} 完成。
     * 任一题型题库数量不足时整卷失败并给出明确提示（需要几道、实际几道），
     * 不会生成一份缺题的试卷。</p>
     *
     * <p>同一题型只允许配置一条规则：否则两条规则可能抽到同一道题，
     * 既违反唯一约束，也会让「每题分值」变得含糊。</p>
     */
    @Transactional
    public ExamPaper createRandom(RandomPaperForm form, User operator) {
        QuestionService.requireTeacher(operator);

        Set<String> types = new HashSet<String>();
        ExamPaper paper = new ExamPaper();
        paper.setTitle(form.getTitle().trim());
        paper.setSubject(form.getSubject().trim());
        paper.setDurationMinutes(form.getDurationMinutes());
        paper.setPassScore(form.getPassScore());
        paper.setStatus(ExamPaper.STATUS_DRAFT);
        paper.setCreatorId(operator.getId());

        List<ExamPaperQuestion> paperQuestions = new ArrayList<ExamPaperQuestion>();
        BigDecimal totalScore = BigDecimal.ZERO;
        int sortOrder = 1;

        for (RandomPaperForm.RandomRule rule : form.getRules()) {
            if (!types.add(rule.getType())) {
                throw new BusinessException("同一题型只能配置一条抽题规则，请合并后再试");
            }
            List<Long> pickedIds = questionMapper.findRandomIds(
                paper.getSubject(), rule.getType(), rule.getCount());
            if (pickedIds.size() < rule.getCount()) {
                throw new BusinessException("题库中「" + paper.getSubject() + "」的「"
                    + typeText(rule.getType()) + "」数量不足：需要 " + rule.getCount()
                    + " 道，实际只有 " + pickedIds.size() + " 道");
            }
            for (Long questionId : pickedIds) {
                ExamPaperQuestion pq = new ExamPaperQuestion();
                pq.setQuestionId(questionId);
                pq.setScore(rule.getScore());
                pq.setSortOrder(sortOrder++);
                paperQuestions.add(pq);
                totalScore = totalScore.add(rule.getScore());
            }
        }
        checkPassScore(form.getPassScore(), totalScore);
        paper.setTotalScore(totalScore);
        return savePaper(paper, paperQuestions);
    }

    /** 试卷详情（含题目数与创建人） */
    public ExamPaper detail(Long paperId) {
        ExamPaper paper = paperMapper.findById(paperId);
        if (paper == null) {
            throw new BusinessException(404, "试卷不存在或已被删除");
        }
        return paper;
    }

    /** 试卷包含的题目（含题干与选项，按卷面顺序） */
    public List<ExamPaperQuestion> listQuestions(Long paperId) {
        return paperQuestionMapper.findByPaperId(paperId);
    }

    /** 教师端试卷列表 */
    public PageResult<ExamPaper> list(String status, Long creatorId, int pageNum, int pageSize) {
        int safeSize = pageSize < 1 ? 10 : Math.min(pageSize, 50);
        int offset = (Math.max(pageNum, 1) - 1) * safeSize;
        long total = paperMapper.countPapers(status, creatorId);
        List<ExamPaper> list = total == 0
            ? Collections.<ExamPaper>emptyList()
            : paperMapper.listPapers(status, creatorId, offset, safeSize);
        return new PageResult<ExamPaper>(list, total, pageNum, safeSize);
    }

    /** 学生端可参加的考试列表 */
    public PageResult<ExamPaper> listPublishedForStudent(Long studentId, int pageNum, int pageSize) {
        int safeSize = pageSize < 1 ? 10 : Math.min(pageSize, 50);
        int offset = (Math.max(pageNum, 1) - 1) * safeSize;
        long total = paperMapper.countPublished();
        List<ExamPaper> list = total == 0
            ? Collections.<ExamPaper>emptyList()
            : paperMapper.listPublishedForStudent(studentId, offset, safeSize);
        return new PageResult<ExamPaper>(list, total, pageNum, safeSize);
    }

    /**
     * 发布 / 结束考试。
     *
     * <p>状态流转做白名单校验：只能 DRAFT→PUBLISHED、PUBLISHED→CLOSED，
     * 不接受前端随意传字符串写库。</p>
     */
    @Transactional
    public ExamPaper updateStatus(Long paperId, String targetStatus, User operator) {
        QuestionService.requireTeacher(operator);
        ExamPaper paper = detail(paperId);
        if (!ExamPaper.STATUS_PUBLISHED.equals(targetStatus)
            && !ExamPaper.STATUS_CLOSED.equals(targetStatus)) {
            throw new BusinessException("不支持的试卷状态：" + targetStatus);
        }
        if (ExamPaper.STATUS_PUBLISHED.equals(targetStatus)
            && paperQuestionMapper.countByPaperId(paperId) == 0) {
            throw new BusinessException("试卷里还没有题目，无法发布");
        }
        paperMapper.updateStatus(paperId, targetStatus);
        return paperMapper.findById(paperId);
    }

    /**
     * 删除试卷。
     *
     * <p>只允许删除还没有学生考过的草稿：一旦有考试记录，删除会让成绩与答卷失去归属，
     * 正确做法是把试卷置为「已结束」。</p>
     */
    @Transactional
    public void delete(Long paperId, User operator) {
        QuestionService.requireTeacher(operator);
        ExamPaper paper = detail(paperId);
        if (recordMapper.countByPaper(paperId) > 0) {
            throw new BusinessException("该试卷已有学生参加，不能删除；如需停止考试请改为「结束」");
        }
        // 先删关联再删主记录，避免外键约束报错
        paperQuestionMapper.deleteByPaperId(paperId);
        paperMapper.deleteById(paperId);
    }

    /** 把试卷与题目关联一次性落库；主记录先插入以获得自增 ID */
    private ExamPaper savePaper(ExamPaper paper, List<ExamPaperQuestion> paperQuestions) {
        paperMapper.insert(paper);
        for (ExamPaperQuestion pq : paperQuestions) {
            pq.setPaperId(paper.getId());
            paperQuestionMapper.insert(pq);
        }
        return paperMapper.findById(paper.getId());
    }

    /** 题目分值：优先取表单覆盖值，否则用题库默认分值 */
    private BigDecimal resolveScore(PaperForm form, Long questionId, Question question) {
        if (form.getQuestionScores() != null) {
            BigDecimal override = form.getQuestionScores().get(questionId);
            if (override != null && override.compareTo(BigDecimal.ZERO) > 0) {
                return override;
            }
        }
        if (question.getScore() == null || question.getScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("题目「" + question.getContent() + "」没有设置分值，无法组卷");
        }
        return question.getScore();
    }

    /** 及格分不能高于总分，否则这场考试没人能及格 */
    private void checkPassScore(BigDecimal passScore, BigDecimal totalScore) {
        if (totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("试卷总分必须大于 0");
        }
        if (passScore.compareTo(totalScore) > 0) {
            throw new BusinessException("及格分（" + passScore + "）不能高于试卷总分（" + totalScore + "）");
        }
    }

    private String typeText(String type) {
        if (Question.TYPE_SINGLE.equals(type)) {
            return "单选题";
        }
        if (Question.TYPE_MULTIPLE.equals(type)) {
            return "多选题";
        }
        if (Question.TYPE_JUDGE.equals(type)) {
            return "判断题";
        }
        return type;
    }
}
