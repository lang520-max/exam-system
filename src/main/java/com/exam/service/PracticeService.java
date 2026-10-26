package com.exam.service;

import com.exam.common.constant.QuestionType;
import com.exam.common.exception.BusinessException;
import com.exam.dto.BankQuestionCount;
import com.exam.dto.CategoryCount;
import com.exam.dto.response.PracticeBankVO;
import com.exam.dto.response.PracticeResultVO;
import com.exam.dto.response.QuestionVO;
import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.mapper.QuestionBankMapper;
import com.exam.mapper.QuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自由练习。
 *
 * <p><b>为什么不复用考试链路</b>：练习不该写 {@code exam_record} ——
 * 那会把「我的成绩」「及格率」「统计分布」全部污染（一次练习就是一条无意义的记录）。
 * 所以这里只读题库、只判一道题，**不落任何库**：练完即走。</p>
 *
 * <p><b>答案怎么给</b>：抽题接口只返回 {@link QuestionVO}（不含答案与解析），
 * 学生提交作答后 {@link #check} 才把参考答案与解析给出来。这样一轮练习的答案
 * 不能被一次性拿走（想拿就得逐题作答，和真实练习的节奏一致）。</p>
 *
 * <p><b>判分口径与正式考试一致</b>：客观题直接复用
 * {@link GradingEngine#normalize(Integer, String)} 再比较，
 * 不另写一套「练习版判分」—— 否则同一道题在练习里算对、在考试里算错，
 * 是最难解释的一类不一致。</p>
 */
@Slf4j
@Service
public class PracticeService {

    /** 一轮最多抽多少题（防止有人把整个题库一次拉走） */
    private static final int MAX_COUNT = 50;

    private final QuestionMapper questionMapper;
    private final QuestionBankMapper bankMapper;

    public PracticeService(QuestionMapper questionMapper, QuestionBankMapper bankMapper) {
        this.questionMapper = questionMapper;
        this.bankMapper = bankMapper;
    }

    /**
     * 可供练习的题库（含可用题量）。题量为 0 的库不返回 —— 学生点进去只会看到空白。
     */
    public List<PracticeBankVO> listBanks() {
        List<BankQuestionCount> counts = questionMapper.countByBank();
        Map<Long, Integer> totalByBank = new LinkedHashMap<>();
        if (counts != null) {
            for (BankQuestionCount c : counts) {
                if (c.getBankId() != null) {
                    totalByBank.put(c.getBankId(), c.getTotal() == null ? 0 : c.getTotal());
                }
            }
        }

        List<PracticeBankVO> result = new ArrayList<>();
        for (QuestionBank bank : bankMapper.selectList(null)) {
            Integer total = totalByBank.get(bank.getId());
            if (total == null || total <= 0) {
                continue;
            }
            PracticeBankVO vo = new PracticeBankVO();
            vo.setId(bank.getId());
            vo.setName(bank.getName());
            vo.setSubject(bank.getSubject());
            vo.setTotal(total);
            result.add(vo);
        }
        return result;
    }

    /**
     * 随机抽题（不含答案与解析）。
     *
     * @param bankId     题库，可选
     * @param type       题型，可选
     * @param difficulty 难度，可选
     * @param category   知识点/专题（V4），可选 —— 选它就能做「一元函数积分学 / 语法」这类专项练习
     * @param count      抽几道（1..50，越界会夹紧而不是报错 —— 页面上加减数量不该失败）
     */
    public List<QuestionVO> sample(Long bankId, Integer type, Integer difficulty, String category, int count) {
        int limit = Math.max(1, Math.min(count, MAX_COUNT));
        String cat = (category == null || category.isBlank()) ? null : category.trim();
        List<Question> questions = questionMapper.selectRandomForPractice(bankId, type, difficulty, cat, limit);
        return QuestionVO.from(questions == null ? List.of() : questions);
    }

    /**
     * 可练习的知识点（含题量），供练习页的「专题」下拉使用。
     *
     * <p>学生也要能看到这些标签 —— 所以不能复用教师专属的
     * {@code /api/questions/categories}；但查询本身是同一段 SQL（共用 mapper 方法），
     * 避免两处口径漂移。</p>
     */
    public List<CategoryCount> listCategories(Long bankId) {
        List<CategoryCount> rows = questionMapper.countByCategory(bankId);
        return rows == null ? List.of() : rows;
    }

    /**
     * 对答案：提交之后才把参考答案与解析给出来。
     *
     * <p>客观题（单选/多选/判断）自动判定，判据与考试完全相同；
     * 填空/简答没有可靠的自动判据，返回 {@code judged=false} + 参考答案，由学生自评。</p>
     */
    public PracticeResultVO check(Long questionId, String studentAnswer) {
        Question q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new BusinessException(404, "题目不存在");
        }

        PracticeResultVO vo = new PracticeResultVO();
        vo.setQuestionId(questionId);
        vo.setType(q.getType());
        vo.setStudentAnswer(studentAnswer);
        vo.setCorrectAnswer(q.getAnswer());
        vo.setAnalysis(q.getAnalysis());

        if (QuestionType.isObjective(q.getType())) {
            String expected = GradingEngine.normalize(q.getType(), q.getAnswer());
            String actual = GradingEngine.normalize(q.getType(), studentAnswer);
            vo.setJudged(true);
            vo.setCorrect(expected != null && expected.equals(actual));
        } else {
            vo.setJudged(false);
            vo.setCorrect(null);
        }
        return vo;
    }
}