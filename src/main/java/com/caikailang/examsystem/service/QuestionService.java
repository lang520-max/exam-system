package com.caikailang.examsystem.service;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.QuestionForm;
import com.caikailang.examsystem.dto.QuestionQuery;
import com.caikailang.examsystem.mapper.ExamPaperQuestionMapper;
import com.caikailang.examsystem.mapper.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 题库服务：题目增删改查。
 *
 * <p>写操作都要求操作者具备教师角色。拦截器已经挡了一层，这里再校验一次：
 * Service 是业务规则的最终防线，将来新增入口（定时任务、内部调用）时不会失守。</p>
 *
 * @author lang520-max
 */
@Service
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;

    public QuestionService(QuestionMapper questionMapper, ExamPaperQuestionMapper paperQuestionMapper) {
        this.questionMapper = questionMapper;
        this.paperQuestionMapper = paperQuestionMapper;
    }

    /** 新增题目 */
    @Transactional
    public Question create(QuestionForm form, User operator) {
        requireTeacher(operator);
        prepare(form);
        Question question = new Question();
        copy(form, question);
        question.setCreatorId(operator.getId());
        questionMapper.insert(question);
        return questionMapper.findById(question.getId());
    }

    /** 编辑题目 */
    @Transactional
    public Question update(Long id, QuestionForm form, User operator) {
        requireTeacher(operator);
        Question exists = questionMapper.findById(id);
        if (exists == null) {
            throw new BusinessException(404, "题目不存在或已被删除");
        }
        prepare(form);
        Question question = new Question();
        question.setId(id);
        copy(form, question);
        questionMapper.update(question);
        return questionMapper.findById(id);
    }

    /**
     * 删除题目。
     *
     * <p>先查引用再删：被试卷引用的题目如果直接删，会触发外键约束报出难懂的数据库异常，
     * 也可能让历史答卷出现「题目已不存在」的悬空数据。</p>
     */
    @Transactional
    public void delete(Long id, User operator) {
        requireTeacher(operator);
        Question exists = questionMapper.findById(id);
        if (exists == null) {
            throw new BusinessException(404, "题目不存在或已被删除");
        }
        int used = paperQuestionMapper.countByQuestionId(id);
        if (used > 0) {
            throw new BusinessException("该题目已被 " + used + " 份试卷引用，请先调整相关试卷后再删除");
        }
        questionMapper.deleteById(id);
    }

    public Question detail(Long id) {
        Question question = questionMapper.findById(id);
        if (question == null) {
            throw new BusinessException(404, "题目不存在或已被删除");
        }
        return question;
    }

    /** 分页检索（教师题库页 / 组卷选题页共用） */
    public PageResult<Question> search(QuestionQuery query) {
        long total = questionMapper.countByQuery(query);
        List<Question> list = total == 0
            ? Collections.<Question>emptyList()
            : questionMapper.search(query);
        return new PageResult<Question>(list, total, query.getPageNum(), query.getSafePageSize());
    }

    /** 题库中已有的科目，供页面下拉框与随机组卷选择 */
    public List<String> listSubjects() {
        return questionMapper.listSubjects();
    }

    /**
     * 校验并规范化表单。
     *
     * <p>校验规则按题型区分，这是注解做不到的跨字段校验：</p>
     * <ul>
     *   <li>单选 / 多选：至少两个非空选项，正确答案必须落在已填写的选项范围内，多选至少两个答案；</li>
     *   <li>判断：清空选项，答案统一归一化成 A（正确）/ B（错误）。</li>
     * </ul>
     */
    void prepare(QuestionForm form) {
        String type = form.getType();
        if (Question.TYPE_JUDGE.equals(type)) {
            // 判断题不使用选项，统一清空，避免脏数据影响页面渲染
            form.setOptionA(null);
            form.setOptionB(null);
            form.setOptionC(null);
            form.setOptionD(null);
            String answer = GradeCalculator.normalize(Question.TYPE_JUDGE, form.getAnswer());
            if (!"A".equals(answer) && !"B".equals(answer)) {
                throw new BusinessException("判断题的答案只能是「正确(A)」或「错误(B)」");
            }
            form.setAnswer(answer);
            return;
        }

        Set<String> available = new LinkedHashSet<String>();
        if (notBlank(form.getOptionA())) {
            available.add("A");
        }
        if (notBlank(form.getOptionB())) {
            available.add("B");
        }
        if (notBlank(form.getOptionC())) {
            available.add("C");
        }
        if (notBlank(form.getOptionD())) {
            available.add("D");
        }
        if (available.size() < 2) {
            throw new BusinessException("单选/多选题至少需要填写两个选项");
        }

        String answer = GradeCalculator.normalize(type, form.getAnswer());
        if (answer == null) {
            throw new BusinessException("正确答案不能为空");
        }
        for (int i = 0; i < answer.length(); i++) {
            if (!available.contains(String.valueOf(answer.charAt(i)))) {
                throw new BusinessException("正确答案「" + answer + "」包含未填写的选项，请检查选项内容");
            }
        }
        if (Question.TYPE_MULTIPLE.equals(type) && answer.length() < 2) {
            throw new BusinessException("多选题的正确答案至少包含两个选项，否则请改为单选题");
        }
        form.setAnswer(answer);
    }

    /** 服务层角色校验：只有教师可以维护题库 */
    static void requireTeacher(User operator) {
        if (operator == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        if (!operator.isTeacher()) {
            throw new BusinessException(403, "该操作仅教师（管理员）可执行");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void copy(QuestionForm form, Question question) {
        question.setType(form.getType());
        question.setSubject(form.getSubject().trim());
        question.setKnowledgePoint(form.getKnowledgePoint());
        question.setContent(form.getContent().trim());
        question.setOptionA(form.getOptionA());
        question.setOptionB(form.getOptionB());
        question.setOptionC(form.getOptionC());
        question.setOptionD(form.getOptionD());
        question.setAnswer(form.getAnswer());
        question.setScore(form.getScore());
        question.setDifficulty(form.getDifficulty());
    }
}
