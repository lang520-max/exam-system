package com.caikailang.examsystem;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.domain.ExamAnswer;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.ExamStats;
import com.caikailang.examsystem.dto.ExamQuestionView;
import com.caikailang.examsystem.dto.PaperForm;
import com.caikailang.examsystem.dto.QuestionForm;
import com.caikailang.examsystem.dto.QuestionQuery;
import com.caikailang.examsystem.dto.RandomPaperForm;
import com.caikailang.examsystem.dto.RegisterForm;
import com.caikailang.examsystem.dto.SubmitForm;
import com.caikailang.examsystem.mapper.ExamAnswerMapper;
import com.caikailang.examsystem.mapper.ExamRecordMapper;
import com.caikailang.examsystem.mapper.UserMapper;
import com.caikailang.examsystem.service.ExamPaperService;
import com.caikailang.examsystem.service.ExamService;
import com.caikailang.examsystem.service.QuestionService;
import com.caikailang.examsystem.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心业务集成测试：真实走 Spring 容器 + H2 数据库 + MyBatis 映射 + 事务。
 *
 * <p>覆盖：题库增删改查、手动组卷、随机组卷、开考、交卷判分、及格判定、
 * 超时标记、防重复交卷、越权拦截、错题回顾、成绩统计。</p>
 *
 * <p>每个用例都用独立科目名（{@code 测试科目-xxxx}）造数据，
 * 因此不会与 H2 里的演示数据互相干扰。</p>
 *
 * @author 蔡开浪
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExamFlowIntegrationTest {

    /** 保证同一个 JVM 内生成的用户名不重复 */
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    @Autowired
    private UserService userService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private ExamPaperService paperService;
    @Autowired
    private ExamService examService;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ExamRecordMapper recordMapper;
    @Autowired
    private ExamAnswerMapper answerMapper;

    /* ================================================================== */
    /* 测试数据构造工具                                                    */
    /* ================================================================== */

    /** 取演示教师账号（data-h2.sql 中 id=1，密码 123456） */
    private User teacher() {
        User teacher = userMapper.findByUsername("teacher");
        assertNotNull(teacher, "H2 演示数据里应当有 teacher 账号");
        teacher.clearPassword();
        return teacher;
    }

    /** 注册一个全新的学生账号（用户名唯一，避免与演示数据或其他用例撞车） */
    private User newStudent() {
        int seq = SEQ.incrementAndGet();
        RegisterForm form = new RegisterForm();
        form.setUsername("it" + Math.abs(System.nanoTime() % 100000000L) + seq);
        form.setPassword("123456");
        form.setNickname("测试学生" + seq);
        return userService.register(form);
    }

    /** 每个用例用独立科目，避免用例之间与演示数据互相干扰 */
    private String newSubject() {
        return "测试科目-" + Math.abs(System.nanoTime() % 100000000L) + "-" + SEQ.incrementAndGet();
    }

    private Question newQuestion(User teacher, String subject, String type, String content,
                                String answer, String score,
                                String a, String b, String c, String d) {
        QuestionForm form = new QuestionForm();
        form.setType(type);
        form.setSubject(subject);
        form.setKnowledgePoint("集成测试知识点");
        form.setContent(content);
        form.setOptionA(a);
        form.setOptionB(b);
        form.setOptionC(c);
        form.setOptionD(d);
        form.setAnswer(answer);
        form.setScore(new BigDecimal(score));
        form.setDifficulty(Question.DIFFICULTY_MEDIUM);
        return questionService.create(form, teacher);
    }

    /**
     * 建一套 8 道题、合计 100 分的标准题库：
     * 单选 4 道 × 10 分 + 多选 2 道 × 20 分 + 判断 2 道 × 10 分。
     */
    private List<Long> standardBank(User teacher, String subject) {
        List<Long> ids = new ArrayList<Long>();
        ids.add(newQuestion(teacher, subject, Question.TYPE_SINGLE, "单选题一：哪个关键字用于定义常量？",
            "A", "10.0", "final", "static", "const", "val").getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_SINGLE, "单选题二：int 占几个字节？",
            "B", "10.0", "2", "4", "8", "16").getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_SINGLE, "单选题三：哪个不是基本数据类型？",
            "C", "10.0", "int", "char", "String", "boolean").getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_SINGLE, "单选题四：抛出异常用哪个关键字？",
            "D", "10.0", "throws", "catch", "finally", "throw").getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_MULTIPLE, "多选题一：面向对象三大特性是？",
            "ABC", "20.0", "封装", "继承", "多态", "编译").getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_MULTIPLE, "多选题二：集合框架顶层接口有？",
            "ABD", "20.0", "List", "Set", "Array", "Map").getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_JUDGE, "判断题一：一个类可以继承多个父类。",
            "B", "10.0", null, null, null, null).getId());
        ids.add(newQuestion(teacher, subject, Question.TYPE_JUDGE, "判断题二：String 内容不可变。",
            "A", "10.0", null, null, null, null).getId());
        return ids;
    }

    /** 标准题库的满分答案 */
    private Map<Long, String> standardCorrectAnswers(List<Long> ids) {
        Map<Long, String> answers = new LinkedHashMap<Long, String>();
        answers.put(ids.get(0), "A");
        answers.put(ids.get(1), "B");
        answers.put(ids.get(2), "C");
        answers.put(ids.get(3), "D");
        answers.put(ids.get(4), "ABC");
        answers.put(ids.get(5), "ABD");
        answers.put(ids.get(6), "B");
        answers.put(ids.get(7), "A");
        return answers;
    }

    /** 用指定题目手动组卷并直接发布 */
    private ExamPaper publishedPaper(User teacher, String subject, List<Long> questionIds, String passScore) {
        PaperForm form = new PaperForm();
        form.setTitle("集成测试试卷");
        form.setSubject(subject);
        form.setDurationMinutes(60);
        form.setPassScore(new BigDecimal(passScore));
        form.setQuestionIds(questionIds);
        ExamPaper draft = paperService.createManual(form, teacher);
        return paperService.updateStatus(draft.getId(), ExamPaper.STATUS_PUBLISHED, teacher);
    }

    private ExamRecord takeExam(ExamPaper paper, User student, Map<Long, String> answers) {
        ExamRecord record = examService.startExam(paper.getId(), student);
        SubmitForm submitForm = new SubmitForm();
        submitForm.setRecordId(record.getId());
        submitForm.setAnswers(answers);
        return examService.submit(submitForm, student);
    }

    /* ================================================================== */
    /* 题库：增删改查与越权                                                 */
    /* ================================================================== */

    @Test
    @DisplayName("题库：学生不能新增题目（服务层 403 越权拦截）")
    void createQuestion_shouldRejectStudent() {
        User student = newStudent();
        QuestionForm form = new QuestionForm();
        form.setType(Question.TYPE_SINGLE);
        form.setSubject(newSubject());
        form.setContent("学生偷偷加的题");
        form.setOptionA("A");
        form.setOptionB("B");
        form.setAnswer("A");
        form.setScore(new BigDecimal("5.0"));
        form.setDifficulty(Question.DIFFICULTY_EASY);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> questionService.create(form, student));
        assertEquals(403, ex.getCode(), "越权应返回 403");
        assertTrue(ex.getMessage().contains("教师"), "提示应说明该操作仅教师可执行");
    }

    @Test
    @DisplayName("题库：学生不能编辑、删除题目，也不能操作试卷")
    void writeOperations_shouldRejectStudent() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        Question question = newQuestion(teacher, subject, Question.TYPE_SINGLE, "被保护的单选",
            "A", "5.0", "A 选项", "B 选项", null, null);

        QuestionForm edit = new QuestionForm();
        edit.setType(Question.TYPE_SINGLE);
        edit.setSubject(subject);
        edit.setContent("被篡改的题干");
        edit.setOptionA("A 选项");
        edit.setOptionB("B 选项");
        edit.setAnswer("A");
        edit.setScore(new BigDecimal("5.0"));
        edit.setDifficulty(Question.DIFFICULTY_EASY);

        assertEquals(403, assertThrows(BusinessException.class,
            () -> questionService.update(question.getId(), edit, student)).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
            () -> questionService.delete(question.getId(), student)).getCode());
        assertEquals("被保护的单选", questionService.detail(question.getId()).getContent(),
            "越权编辑不应生效");

        PaperForm paperForm = new PaperForm();
        paperForm.setTitle("学生组卷");
        paperForm.setSubject(subject);
        paperForm.setDurationMinutes(30);
        paperForm.setPassScore(new BigDecimal("10"));
        paperForm.setQuestionIds(java.util.Collections.singletonList(question.getId()));
        assertEquals(403, assertThrows(BusinessException.class,
            () -> paperService.createManual(paperForm, student)).getCode());
    }

    @Test
    @DisplayName("题库：答案必须落在已填写的选项范围内，多选至少两个答案")
    void createQuestion_shouldValidateAnswerAgainstOptions() {
        User teacher = teacher();
        String subject = newSubject();

        // 只填了 A、B 两个选项，答案却是 D
        BusinessException outOfRange = assertThrows(BusinessException.class,
            () -> newQuestion(teacher, subject, Question.TYPE_SINGLE, "答案超纲的题",
                "D", "5.0", "A 选项", "B 选项", null, null));
        assertTrue(outOfRange.getMessage().contains("未填写"), "应提示答案超出选项范围");

        // 选项少于两个
        BusinessException tooFewOptions = assertThrows(BusinessException.class,
            () -> newQuestion(teacher, subject, Question.TYPE_SINGLE, "只有一个选项的题",
                "A", "5.0", "A 选项", null, null, null));
        assertTrue(tooFewOptions.getMessage().contains("两个选项"));

        // 多选题只给了一个答案
        BusinessException singleAnswer = assertThrows(BusinessException.class,
            () -> newQuestion(teacher, subject, Question.TYPE_MULTIPLE, "多选却只给一个答案",
                "A", "5.0", "A 选项", "B 选项", null, null));
        assertTrue(singleAnswer.getMessage().contains("多选题"));
    }

    @Test
    @DisplayName("题库：判断题自动清空选项，答案归一化为 A/B")
    void createQuestion_shouldNormalizeJudge() {
        User teacher = teacher();
        String subject = newSubject();

        // 故意给判断题填了选项，并用中文「正确」作答案
        Question judge = newQuestion(teacher, subject, Question.TYPE_JUDGE, "判断题：Java 支持多继承。",
            "正确", "5.0", "对", "错", "不确定", "以上都不是");

        assertEquals("A", judge.getAnswer(), "「正确」应归一化为 A");
        assertNull(judge.getOptionA(), "判断题不应该保留选项");
        assertNull(judge.getOptionB());
        assertNull(judge.getOptionC());
        assertNull(judge.getOptionD());

        // 判断题答案必须能识别
        BusinessException ex = assertThrows(BusinessException.class,
            () -> newQuestion(teacher, subject, Question.TYPE_JUDGE, "答案无法识别的判断题",
                "大概是错的", "5.0", null, null, null, null));
        assertTrue(ex.getMessage().contains("判断题"));
    }

    @Test
    @DisplayName("题库：草稿试卷已引用的题目不允许删除（先查引用再删，避免外键异常）")
    void deleteQuestion_shouldRejectWhenUsedByPaper() {
        User teacher = teacher();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        publishedPaper(teacher, subject, ids, "60");

        BusinessException ex = assertThrows(BusinessException.class,
            () -> questionService.delete(ids.get(0), teacher));
        assertTrue(ex.getMessage().contains("已被"), "应提示题目已被试卷引用");
        assertNotNull(questionService.detail(ids.get(0)), "引用了的题目必须还在");
    }

    @Test
    @DisplayName("题库：按关键字 + 题型 + 科目检索，分页与总数口径一致")
    void searchQuestions_shouldFilterByKeywordAndType() {
        User teacher = teacher();
        String subject = newSubject();
        standardBank(teacher, subject);

        QuestionQuery bySubject = new QuestionQuery();
        bySubject.setSubject(subject);
        bySubject.setPageSize(50);
        PageResult<Question> subjectPage = questionService.search(bySubject);
        assertEquals(8, subjectPage.getTotal(), "该科目下应有 8 道题");
        assertTrue(subjectPage.getList().stream()
            .allMatch(q -> subject.equals(q.getSubject())));

        QuestionQuery byType = new QuestionQuery();
        byType.setSubject(subject);
        byType.setType(Question.TYPE_MULTIPLE);
        byType.setPageSize(50);
        PageResult<Question> multiplePage = questionService.search(byType);
        assertEquals(2, multiplePage.getTotal());
        assertTrue(multiplePage.getList().stream()
            .allMatch(q -> Question.TYPE_MULTIPLE.equals(q.getType())));

        QuestionQuery byKeyword = new QuestionQuery();
        byKeyword.setSubject(subject);
        byKeyword.setKeyword("哪个关键字用于定义常量");
        byKeyword.setPageSize(50);
        assertEquals(1, questionService.search(byKeyword).getTotal());
        assertTrue(questionService.search(byKeyword).getList().get(0).getContent().contains("常量"));

        // 分页：每页 3 条，第 2 页应有 3 条，总数仍是 8
        QuestionQuery paged = new QuestionQuery();
        paged.setSubject(subject);
        paged.setPageNum(2);
        paged.setPageSize(3);
        PageResult<Question> page2 = questionService.search(paged);
        assertEquals(8, page2.getTotal());
        assertEquals(3, page2.getList().size());
        assertEquals(3, page2.getPages());

        QuestionQuery noMatch = new QuestionQuery();
        noMatch.setSubject(subject);
        noMatch.setKeyword("绝对不存在的题目关键字ZZZ");
        assertEquals(0, questionService.search(noMatch).getTotal());
        assertTrue(questionService.search(noMatch).getList().isEmpty());

        // 排序字段白名单：非法值不会拼进 SQL，接口照样正常返回
        QuestionQuery evilSort = new QuestionQuery();
        evilSort.setSubject(subject);
        evilSort.setSortBy("id; DROP TABLE question--");
        evilSort.setSortDir("asc; DROP TABLE exam_record--");
        assertEquals(8, questionService.search(evilSort).getTotal(),
            "非法排序字段应回落到默认排序，而不是把注入串拼进 ORDER BY");
        assertEquals(8, questionService.search(bySubject).getTotal(),
            "注入串不能破坏表结构");
    }

    /* ================================================================== */
    /* 组卷                                                                */
    /* ================================================================== */

    @Test
    @DisplayName("组卷-手动：总分按题目分值累加，题号连续，初始为草稿")
    void createManualPaper_shouldSumTotalScore() {
        User teacher = teacher();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);

        PaperForm form = new PaperForm();
        form.setTitle("手动组卷测试");
        form.setSubject(subject);
        form.setDurationMinutes(90);
        form.setPassScore(new BigDecimal("60"));
        form.setQuestionIds(ids);

        ExamPaper paper = paperService.createManual(form, teacher);

        assertNotNull(paper.getId());
        assertEquals(0, new BigDecimal("100.0").compareTo(paper.getTotalScore()),
            "10×4 + 20×2 + 10×2 = 100 分");
        assertEquals(8, paper.getQuestionCount().intValue());
        assertEquals(ExamPaper.STATUS_DRAFT, paper.getStatus(), "新组卷应为草稿，需教师手动发布");
        assertEquals(90, paper.getDurationMinutes().intValue());
        assertEquals("王老师", paper.getCreatorNickname(), "应带出创建人昵称（关联查询）");

        // 题号从 1 开始且连续
        List<com.caikailang.examsystem.domain.ExamPaperQuestion> questions =
            paperService.listQuestions(paper.getId());
        assertEquals(8, questions.size());
        for (int i = 0; i < questions.size(); i++) {
            assertEquals(i + 1, questions.get(i).getSortOrder().intValue());
            assertNotNull(questions.get(i).getContent(), "应带出题干");
        }
    }

    @Test
    @DisplayName("组卷-手动：同一道题重复入卷被拒绝；及格分高于总分被拒绝")
    void createManualPaper_shouldRejectInvalidInput() {
        User teacher = teacher();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);

        PaperForm duplicated = new PaperForm();
        duplicated.setTitle("重复题目试卷");
        duplicated.setSubject(subject);
        duplicated.setDurationMinutes(30);
        duplicated.setPassScore(new BigDecimal("10"));
        List<Long> withDuplicate = new ArrayList<Long>(ids);
        withDuplicate.add(ids.get(0));
        duplicated.setQuestionIds(withDuplicate);
        BusinessException ex = assertThrows(BusinessException.class,
            () -> paperService.createManual(duplicated, teacher));
        assertTrue(ex.getMessage().contains("重复"), "应提示同一道题不能重复出现");

        PaperForm badPass = new PaperForm();
        badPass.setTitle("及格分超总分");
        badPass.setSubject(subject);
        badPass.setDurationMinutes(30);
        badPass.setPassScore(new BigDecimal("200"));
        badPass.setQuestionIds(ids);
        BusinessException passEx = assertThrows(BusinessException.class,
            () -> paperService.createManual(badPass, teacher));
        assertTrue(passEx.getMessage().contains("及格分"));
    }

    @Test
    @DisplayName("组卷-随机：按「科目+题型+数量+每题分值」抽题，数量与总分正确")
    void createRandomPaper_shouldPickRequestedCounts() {
        User teacher = teacher();
        String subject = newSubject();
        // 5 单选 + 3 多选 + 3 判断，足够抽 3/2/2
        for (int i = 0; i < 5; i++) {
            newQuestion(teacher, subject, Question.TYPE_SINGLE, "随机抽题单选" + i,
                "A", "3.0", "A 选项", "B 选项", "C 选项", "D 选项");
        }
        for (int i = 0; i < 3; i++) {
            newQuestion(teacher, subject, Question.TYPE_MULTIPLE, "随机抽题多选" + i,
                "AB", "6.0", "A 选项", "B 选项", "C 选项", "D 选项");
        }
        for (int i = 0; i < 3; i++) {
            newQuestion(teacher, subject, Question.TYPE_JUDGE, "随机抽题判断" + i,
                "A", "2.0", null, null, null, null);
        }

        RandomPaperForm form = new RandomPaperForm();
        form.setTitle("随机抽题试卷");
        form.setSubject(subject);
        form.setDurationMinutes(45);
        form.setPassScore(new BigDecimal("20"));
        List<RandomPaperForm.RandomRule> rules = new ArrayList<RandomPaperForm.RandomRule>();
        rules.add(rule(Question.TYPE_SINGLE, 3, "5.0"));
        rules.add(rule(Question.TYPE_MULTIPLE, 2, "10.0"));
        rules.add(rule(Question.TYPE_JUDGE, 2, "5.0"));
        form.setRules(rules);

        ExamPaper paper = paperService.createRandom(form, teacher);

        assertEquals(7, paper.getQuestionCount().intValue(), "3 + 2 + 2 = 7 道题");
        assertEquals(0, new BigDecimal("45.0").compareTo(paper.getTotalScore()),
            "3×5 + 2×10 + 2×5 = 45 分");

        List<com.caikailang.examsystem.domain.ExamPaperQuestion> questions =
            paperService.listQuestions(paper.getId());
        long singleCount = questions.stream()
            .filter(q -> Question.TYPE_SINGLE.equals(q.getType())).count();
        long multipleCount = questions.stream()
            .filter(q -> Question.TYPE_MULTIPLE.equals(q.getType())).count();
        long judgeCount = questions.stream()
            .filter(q -> Question.TYPE_JUDGE.equals(q.getType())).count();
        assertEquals(3, singleCount);
        assertEquals(2, multipleCount);
        assertEquals(2, judgeCount);
        assertTrue(questions.stream().allMatch(q -> subject.equals(q.getSubject())),
            "抽出来的题必须都属于指定科目");
        assertEquals(7, questions.stream().map(q -> q.getQuestionId()).distinct().count(),
            "抽题不能重复");
    }

    @Test
    @DisplayName("组卷-随机：题库数量不足时整卷失败并给出明确提示")
    void createRandomPaper_shouldFailWhenQuestionsNotEnough() {
        User teacher = teacher();
        String subject = newSubject();
        newQuestion(teacher, subject, Question.TYPE_SINGLE, "题库里只有这一道单选",
            "A", "5.0", "A 选项", "B 选项", "C 选项", "D 选项");

        RandomPaperForm form = new RandomPaperForm();
        form.setTitle("题目不足的随机卷");
        form.setSubject(subject);
        form.setDurationMinutes(30);
        form.setPassScore(new BigDecimal("10"));
        List<RandomPaperForm.RandomRule> rules = new ArrayList<RandomPaperForm.RandomRule>();
        rules.add(rule(Question.TYPE_SINGLE, 10, "5.0"));
        form.setRules(rules);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> paperService.createRandom(form, teacher));
        assertTrue(ex.getMessage().contains("数量不足"), "应提示需要几道、实际几道");
        assertTrue(ex.getMessage().contains("10"), "提示里应写明需要的数量");
    }

    @Test
    @DisplayName("组卷-随机：同一题型配置两条规则被拒绝（否则可能抽到同一道题）")
    void createRandomPaper_shouldRejectDuplicatedTypeRule() {
        User teacher = teacher();
        String subject = newSubject();
        for (int i = 0; i < 4; i++) {
            newQuestion(teacher, subject, Question.TYPE_SINGLE, "规则重复测试单选" + i,
                "A", "5.0", "A 选项", "B 选项", "C 选项", "D 选项");
        }

        RandomPaperForm form = new RandomPaperForm();
        form.setTitle("规则重复的随机卷");
        form.setSubject(subject);
        form.setDurationMinutes(30);
        form.setPassScore(new BigDecimal("10"));
        List<RandomPaperForm.RandomRule> rules = new ArrayList<RandomPaperForm.RandomRule>();
        rules.add(rule(Question.TYPE_SINGLE, 2, "5.0"));
        rules.add(rule(Question.TYPE_SINGLE, 2, "10.0"));
        form.setRules(rules);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> paperService.createRandom(form, teacher));
        assertTrue(ex.getMessage().contains("同一题型"));
    }

    private RandomPaperForm.RandomRule rule(String type, int count, String score) {
        RandomPaperForm.RandomRule rule = new RandomPaperForm.RandomRule();
        rule.setType(type);
        rule.setCount(Integer.valueOf(count));
        rule.setScore(new BigDecimal(score));
        return rule;
    }

    @Test
    @DisplayName("组卷：不支持的状态值被拒绝；已考过的试卷不能删除；结束后学生端不再可见")
    void publishAndDelete_shouldCheckPreconditions() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        // 状态值走白名单，前端随便传字符串写不进去
        BusinessException badStatus = assertThrows(BusinessException.class,
            () -> paperService.updateStatus(paper.getId(), "WHATEVER", teacher));
        assertTrue(badStatus.getMessage().contains("不支持的试卷状态"));

        // 已经有学生考过之后不能删
        takeExam(paper, student, standardCorrectAnswers(ids));
        BusinessException deleteEx = assertThrows(BusinessException.class,
            () -> paperService.delete(paper.getId(), teacher));
        assertTrue(deleteEx.getMessage().contains("不能删除"));
        assertEquals(ExamPaper.STATUS_PUBLISHED, paperService.detail(paper.getId()).getStatus(),
            "删除失败不应改变试卷状态");

        // 结束考试：状态变更成功，学生端不再出现
        ExamPaper closed = paperService.updateStatus(paper.getId(), ExamPaper.STATUS_CLOSED, teacher);
        assertEquals(ExamPaper.STATUS_CLOSED, closed.getStatus());
        assertEquals("已结束", closed.getStatusText());
        assertTrue(paperService.listPublishedForStudent(student.getId(), 1, 50).getList().stream()
                .noneMatch(p -> p.getId().equals(paper.getId())),
            "结束的考试不该再出现在学生端列表");
        // 历史成绩仍然保留
        assertEquals(1, examService.paperRecords(queryFor(paper.getId()), teacher).getTotal());
    }

    private ExamRecordQuery queryFor(Long paperId) {
        ExamRecordQuery query = new ExamRecordQuery();
        query.setPaperId(paperId);
        return query;
    }

    /* ================================================================== */
    /* 考试全流程与判分                                                     */
    /* ================================================================== */

    @Test
    @DisplayName("考试全流程：组卷→发布→开考→答题→交卷，满分 100 且判定及格")
    void fullExamFlow_shouldGradeFullScoreAndPass() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        ExamRecord record = takeExam(paper, student, standardCorrectAnswers(ids));

        assertEquals(ExamRecord.STATUS_SUBMITTED, record.getStatus());
        assertEquals(0, new BigDecimal("100.0").compareTo(record.getScore()), "全对应得满分 100");
        assertEquals(8, record.getCorrectCount().intValue());
        assertEquals(8, record.getTotalCount().intValue());
        assertFalse(record.getTimeout().booleanValue(), "正常交卷不应标记超时");
        assertNotNull(record.getSubmitTime(), "交卷时间必须落库");
        assertTrue(record.isPassed(), "100 分 >= 及格线 60 分");
        assertEquals("集成测试试卷", record.getPaperTitle(), "应带出试卷标题（关联查询）");
        assertEquals(100, record.getScorePercent(), "100 / 100 = 100%");

        // 答卷明细：8 道题各一条，全部答对
        List<ExamAnswer> answers = examService.review(record.getId(), student);
        assertEquals(8, answers.size());
        assertTrue(answers.stream().allMatch(a -> Boolean.TRUE.equals(a.getCorrect())));
        assertNotNull(answers.get(0).getContent(), "成绩单应带出题干（关联查询）");
        assertNotNull(answers.get(0).getCorrectAnswer(), "成绩单应带出正确答案快照");
        assertEquals(0, new BigDecimal("10.0").compareTo(answers.get(0).getScore()),
            "成绩单应带出该题满分");

        // 错题回顾应为空
        assertTrue(examService.wrongAnswers(record.getId(), student).isEmpty());
    }

    @Test
    @DisplayName("判分：多选题少选不得分，得分与及格判定随实际作答变化")
    void submit_shouldGradePartialScore() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        Map<Long, String> answers = standardCorrectAnswers(ids);
        // 第 5 题是多选（20 分），标准答案 ABC，这里只选 AB —— 按规则少选不得分
        answers.put(ids.get(4), "AB");

        ExamRecord record = takeExam(paper, student, answers);

        assertEquals(0, new BigDecimal("80.0").compareTo(record.getScore()),
            "少选一道 20 分的多选题应丢 20 分，得 80");
        assertEquals(7, record.getCorrectCount().intValue());
        assertTrue(record.isPassed(), "80 分仍然及格");

        List<ExamAnswer> wrongs = examService.wrongAnswers(record.getId(), student);
        assertEquals(1, wrongs.size(), "只有少选的那道多选题应进错题本");
        assertEquals(ids.get(4), wrongs.get(0).getQuestionId());
        assertEquals(0, BigDecimal.ZERO.compareTo(wrongs.get(0).getEarnedScore()));
        assertEquals("AB", wrongs.get(0).getStudentAnswer());
        assertEquals("ABC", wrongs.get(0).getCorrectAnswer(), "应留下正确答案快照");
    }

    @Test
    @DisplayName("判分：大面积不作答则不及格，未作答的题也写入答卷明细")
    void submit_shouldFailWhenScoreBelowPassLine() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        // 只答对前两道单选（各 10 分），其余不作答
        Map<Long, String> answers = new LinkedHashMap<Long, String>();
        answers.put(ids.get(0), "A");
        answers.put(ids.get(1), "B");

        ExamRecord record = takeExam(paper, student, answers);

        assertEquals(0, new BigDecimal("20.0").compareTo(record.getScore()));
        assertEquals(2, record.getCorrectCount().intValue());
        assertEquals(8, record.getTotalCount().intValue());
        assertFalse(record.isPassed(), "20 分 < 及格线 60 分");

        List<ExamAnswer> all = examService.review(record.getId(), student);
        assertEquals(8, all.size(), "未作答的题也必须留明细，成绩单不能缺题");
        assertEquals(6, all.stream().filter(a -> a.getStudentAnswer() == null).count());
        assertEquals("未作答", all.stream()
            .filter(a -> a.getStudentAnswer() == null).findFirst().get().getStudentAnswerText());
    }

    @Test
    @DisplayName("判分：超过「开考时间 + 时长」交卷仍可提交，但记录标记超时")
    void submit_shouldMarkTimeoutWhenOverdue() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        // 直接造一条 2 小时前开考的记录（试卷时长 60 分钟），模拟断网后补交
        ExamRecord stale = new ExamRecord();
        stale.setPaperId(paper.getId());
        stale.setStudentId(student.getId());
        stale.setStartTime(new Date(System.currentTimeMillis() - 120L * 60L * 1000L));
        stale.setScore(BigDecimal.ZERO);
        stale.setCorrectCount(0);
        stale.setTotalCount(8);
        stale.setStatus(ExamRecord.STATUS_ONGOING);
        stale.setTimeout(Boolean.FALSE);
        // durationMinutes 是关联查询带出的展示字段（不落库），这里手工回填以便断言截止时刻
        stale.setDurationMinutes(paper.getDurationMinutes());
        recordMapper.insert(stale);

        assertTrue(stale.getDeadline().before(new Date()), "截止时刻应已过去");

        SubmitForm form = new SubmitForm();
        form.setRecordId(stale.getId());
        form.setAnswers(standardCorrectAnswers(ids));
        ExamRecord submitted = examService.submit(form, student);

        assertEquals(ExamRecord.STATUS_SUBMITTED, submitted.getStatus(), "超时不应拒绝交卷");
        assertTrue(submitted.getTimeout().booleanValue(), "超时交卷必须打上标记");
        assertEquals(0, new BigDecimal("100.0").compareTo(submitted.getScore()),
            "超时交卷的分数照常计算");
    }

    /* ================================================================== */
    /* 防重复交卷与并发                                                     */
    /* ================================================================== */

    @Test
    @DisplayName("防重：重复交卷被条件更新拦下，分数与答卷明细不会被写第二次")
    void submit_shouldRejectDuplicateSubmission() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        ExamRecord record = takeExam(paper, student, standardCorrectAnswers(ids));
        assertEquals(0, new BigDecimal("100.0").compareTo(record.getScore()));

        // 第二次交卷：这次故意全部答错，如果不能拦截，分数会被改写成 0
        Map<Long, String> wrongAnswers = new LinkedHashMap<Long, String>();
        for (Long id : ids) {
            wrongAnswers.put(id, "Z");
        }
        SubmitForm second = new SubmitForm();
        second.setRecordId(record.getId());
        second.setAnswers(wrongAnswers);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> examService.submit(second, student));
        assertTrue(ex.getMessage().contains("请勿重复提交"), "应提示重复交卷");

        ExamRecord after = recordMapper.findById(record.getId());
        assertEquals(0, new BigDecimal("100.0").compareTo(after.getScore()),
            "重复交卷不能改写已经算好的分数");
        assertEquals(8, answerMapper.findByRecordId(record.getId()).size(),
            "答卷明细不能出现重复行");
    }

    @Test
    @DisplayName("防重：已交卷的考试不能再次开考；未交卷时重复开考不会重置计时")
    void startExam_shouldBeIdempotentAndBlockRestart() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        ExamRecord first = examService.startExam(paper.getId(), student);
        ExamRecord second = examService.startExam(paper.getId(), student);

        assertEquals(first.getId(), second.getId(), "同一场考试只能有一条考试记录");
        assertEquals(first.getStartTime(), second.getStartTime(),
            "重复开考不能刷新开始时间，否则刷新页面就能重置考试时长");
        assertEquals(ExamRecord.STATUS_ONGOING, second.getStatus());

        // 交卷后不允许再开考
        SubmitForm form = new SubmitForm();
        form.setRecordId(first.getId());
        form.setAnswers(standardCorrectAnswers(ids));
        examService.submit(form, student);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> examService.startExam(paper.getId(), student));
        assertTrue(ex.getMessage().contains("重复开考"));
    }

    @Test
    @DisplayName("安全：考生答题时拿到的题目不含正确答案")
    void examQuestions_shouldNotLeakCorrectAnswer() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        ExamRecord record = examService.startExam(paper.getId(), student);
        List<ExamQuestionView> questions = examService.getExamQuestions(record.getId(), student);

        assertEquals(8, questions.size());
        assertEquals(1, questions.get(0).getSortOrder());
        assertNotNull(questions.get(0).getContent());
        assertEquals(0, new BigDecimal("10.0").compareTo(questions.get(0).getScore()));

        // ExamQuestionView 根本没有 answer 字段：把对象序列化成 JSON 也不该出现答案
        String json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(questions).toString();
        assertFalse(json.contains("\"answer\""), "下发给学生的题目不能带正确答案字段");
        assertFalse(json.contains("标准答案"), "更不能把答案内容带出去");
    }

    /* ================================================================== */
    /* 越权：学生不能看别人的成绩                                           */
    /* ================================================================== */

    @Test
    @DisplayName("越权：学生不能提交别人的答卷，也不能查看别人的成绩与错题")
    void studentCannotAccessOthersRecord() {
        User teacher = teacher();
        User owner = newStudent();
        User stranger = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        ExamRecord record = examService.startExam(paper.getId(), owner);

        // 1) 别人不能替他交卷
        SubmitForm form = new SubmitForm();
        form.setRecordId(record.getId());
        form.setAnswers(standardCorrectAnswers(ids));
        SubmitForm hijack = new SubmitForm();
        hijack.setRecordId(record.getId());
        hijack.setAnswers(form.getAnswers());
        BusinessException submitEx = assertThrows(BusinessException.class,
            () -> examService.submit(hijack, stranger));
        assertEquals(403, submitEx.getCode());

        // 2) 本人交卷后，别人不能看成绩、不能看答卷、不能看错题
        assertEquals(0, new BigDecimal("100.0").compareTo(
            examService.submit(form, owner).getScore()));

        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.review(record.getId(), stranger)).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.wrongAnswers(record.getId(), stranger)).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.detail(record.getId(), stranger)).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.getExamQuestions(record.getId(), stranger)).getCode());
    }

    @Test
    @DisplayName("越权：学生不能查看某场考试的成绩列表与统计，教师可以")
    void paperRecordsAndStats_shouldBeTeacherOnly() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");
        takeExam(paper, student, standardCorrectAnswers(ids));

        ExamRecordQuery query = new ExamRecordQuery();
        query.setPaperId(paper.getId());

        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.paperRecords(query, student)).getCode());
        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.paperStats(paper.getId(), student)).getCode());

        PageResult<ExamRecord> records = examService.paperRecords(query, teacher);
        assertEquals(1, records.getTotal());
        assertEquals(student.getId(), records.getList().get(0).getStudentId());
        assertNotNull(records.getList().get(0).getStudentNickname(), "应带出考生昵称");
    }

    @Test
    @DisplayName("我的成绩：studentId 以 Session 身份为准，前端传的 studentId 会被覆盖")
    void myRecords_shouldForceSessionStudentId() {
        User teacher = teacher();
        User student = newStudent();
        User other = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");
        takeExam(paper, student, standardCorrectAnswers(ids));

        ExamRecordQuery query = new ExamRecordQuery();
        // 恶意传入别人的 studentId，服务端必须无视它
        query.setStudentId(other.getId());
        PageResult<ExamRecord> mine = examService.myRecords(query, student);

        assertEquals(1, mine.getTotal(), "只能看到自己那一场");
        assertEquals(student.getId(), mine.getList().get(0).getStudentId());

        assertEquals(0, examService.myRecords(new ExamRecordQuery(), other).getTotal(),
            "别人的成绩列表应为空");
    }

    /* ================================================================== */
    /* 成绩统计                                                            */
    /* ================================================================== */

    @Test
    @DisplayName("统计：平均分、及格率、最高/最低分按已交卷记录计算")
    void paperStats_shouldComputeAvgPassRateAndMinMax() {
        User teacher = teacher();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        // 学生甲：满分 100
        takeExam(paper, newStudent(), standardCorrectAnswers(ids));

        // 学生乙：少选一道 20 分的多选题 → 80 分
        User second = newStudent();
        Map<Long, String> partial = standardCorrectAnswers(ids);
        partial.put(ids.get(4), "AB");
        takeExam(paper, second, partial);

        // 学生丙：只答对两道单选 → 20 分，不及格
        User third = newStudent();
        Map<Long, String> poor = new LinkedHashMap<Long, String>();
        poor.put(ids.get(0), "A");
        poor.put(ids.get(1), "B");
        takeExam(paper, third, poor);

        // 学生丁：开了考但没交卷，不应计入统计
        examService.startExam(paper.getId(), newStudent());

        ExamStats stats = examService.paperStats(paper.getId(), teacher);

        assertEquals(3, stats.getTotalCount(), "只统计已交卷的 3 人，未交卷的不计入");
        assertEquals(2, stats.getPassCount(), "100 与 80 及格，20 不及格");
        assertEquals(0, new BigDecimal("66.7").compareTo(stats.getAvgScore()), "平均分 (100+80+20)/3 = 66.7");
        assertEquals(0, new BigDecimal("100.0").compareTo(stats.getMaxScore()));
        assertEquals(0, new BigDecimal("20.0").compareTo(stats.getMinScore()));
        assertEquals(0, new BigDecimal("66.7").compareTo(stats.getPassRate()), "及格率 2/3 = 66.7%");
        assertEquals("集成测试试卷", stats.getPaperTitle());
        assertEquals(0, new BigDecimal("60").compareTo(stats.getPassScore()));
    }

    @Test
    @DisplayName("统计：无人交卷时返回 0 而不是除零异常")
    void paperStats_shouldHandleEmptyPaper() {
        User teacher = teacher();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        ExamStats stats = examService.paperStats(paper.getId(), teacher);

        assertEquals(0, stats.getTotalCount());
        assertEquals(0, stats.getPassCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getAvgScore()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getPassRate()), "0/0 应返回 0.0 而不是抛异常");
    }

    @Test
    @DisplayName("统计：学生个人成绩概览跨试卷汇总")
    void myStats_shouldAggregateAcrossPapers() {
        User teacher = teacher();
        User student = newStudent();

        String subjectA = newSubject();
        List<Long> idsA = standardBank(teacher, subjectA);
        ExamPaper paperA = publishedPaper(teacher, subjectA, idsA, "60");
        takeExam(paperA, student, standardCorrectAnswers(idsA));

        String subjectB = newSubject();
        List<Long> idsB = standardBank(teacher, subjectB);
        ExamPaper paperB = publishedPaper(teacher, subjectB, idsB, "60");
        Map<Long, String> poor = new LinkedHashMap<Long, String>();
        poor.put(idsB.get(0), "A");
        poor.put(idsB.get(1), "B");
        takeExam(paperB, student, poor);

        ExamStats stats = examService.myStats(student);

        assertEquals(2, stats.getTotalCount(), "考了两场");
        assertEquals(1, stats.getPassCount(), "一场及格一场不及格");
        assertEquals(0, new BigDecimal("60.0").compareTo(stats.getAvgScore()), "(100 + 20) / 2 = 60");
        assertEquals(0, new BigDecimal("100.0").compareTo(stats.getMaxScore()));
        assertEquals(0, new BigDecimal("20.0").compareTo(stats.getMinScore()));
        assertEquals(0, new BigDecimal("50.0").compareTo(stats.getPassRate()));
    }

    /* ================================================================== */
    /* 其他：注册、学生端考试列表                                           */
    /* ================================================================== */

    @Test
    @DisplayName("注册：密码以 BCrypt 存储，对外返回的对象不带密码")
    void register_shouldHashPassword() {
        User student = newStudent();

        assertNotNull(student.getId());
        assertNull(student.getPassword(), "对外返回的用户对象不应携带密码");

        User inDb = userMapper.findById(student.getId());
        assertNotNull(inDb.getPassword());
        assertFalse("123456".equals(inDb.getPassword()), "库里不能存明文");
        assertTrue(inDb.getPassword().startsWith("$2a$10$"), "应是强度 10 的 BCrypt 哈希");
    }

    @Test
    @DisplayName("学生端：只列出已发布的试卷，并带出自己在这份卷子上的进度")
    void publishedPapers_shouldExposeStudentProgress() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        // 发布后应出现在学生的可考列表中，且尚未有记录
        PageResult<ExamPaper> before = paperService.listPublishedForStudent(student.getId(), 1, 50);
        ExamPaper listed = before.getList().stream()
            .filter(p -> p.getId().equals(paper.getId())).findFirst().orElse(null);
        assertNotNull(listed, "已发布的试卷应出现在学生端列表");
        assertNull(listed.getRecordId(), "还没开考，不应有考试记录");
        assertEquals(8, listed.getQuestionCount().intValue(), "应带出题目数量");

        // 开考后状态变为未交卷
        ExamRecord record = examService.startExam(paper.getId(), student);
        PageResult<ExamPaper> ongoing = paperService.listPublishedForStudent(student.getId(), 1, 50);
        ExamPaper ongoingPaper = ongoing.getList().stream()
            .filter(p -> p.getId().equals(paper.getId())).findFirst().get();
        assertEquals(record.getId(), ongoingPaper.getRecordId());
        assertEquals(ExamRecord.STATUS_ONGOING, ongoingPaper.getRecordStatus());

        // 交卷后带上分数
        SubmitForm form = new SubmitForm();
        form.setRecordId(record.getId());
        form.setAnswers(standardCorrectAnswers(ids));
        examService.submit(form, student);

        PageResult<ExamPaper> submitted = paperService.listPublishedForStudent(student.getId(), 1, 50);
        ExamPaper submittedPaper = submitted.getList().stream()
            .filter(p -> p.getId().equals(paper.getId())).findFirst().get();
        assertEquals(ExamRecord.STATUS_SUBMITTED, submittedPaper.getRecordStatus());
        assertEquals(0, new BigDecimal("100.0").compareTo(submittedPaper.getRecordScore()));

        // 已结束的试卷不再出现在学生端
        paperService.updateStatus(paper.getId(), ExamPaper.STATUS_CLOSED, teacher);
        PageResult<ExamPaper> afterClose = paperService.listPublishedForStudent(student.getId(), 1, 50);
        assertTrue(afterClose.getList().stream().noneMatch(p -> p.getId().equals(paper.getId())),
            "已结束的考试不应再出现在可考列表");
    }

    @Test
    @DisplayName("开考：未发布的草稿试卷不能参加考试")
    void startExam_shouldRejectUnpublishedPaper() {
        User teacher = teacher();
        User student = newStudent();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);

        PaperForm form = new PaperForm();
        form.setTitle("还在草稿状态的试卷");
        form.setSubject(subject);
        form.setDurationMinutes(30);
        form.setPassScore(new BigDecimal("60"));
        form.setQuestionIds(ids);
        ExamPaper draft = paperService.createManual(form, teacher);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> examService.startExam(draft.getId(), student));
        assertTrue(ex.getMessage().contains("尚未发布"));
    }

    @Test
    @DisplayName("开考：教师账号不能参加考试")
    void startExam_shouldRejectTeacher() {
        User teacher = teacher();
        String subject = newSubject();
        List<Long> ids = standardBank(teacher, subject);
        ExamPaper paper = publishedPaper(teacher, subject, ids, "60");

        assertEquals(403, assertThrows(BusinessException.class,
            () -> examService.startExam(paper.getId(), teacher)).getCode());
    }
}
