package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.exception.BusinessException;
import com.exam.dto.QuestionSnapshot;
import com.exam.dto.request.ExamImportDTO;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.QuestionBankMapper;
import com.exam.mapper.QuestionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 试卷导入服务（JSON 导入）。
 *
 * <p><b>为什么先攒错误再一次抛出</b>：导入是「一次上传、批量处理」的操作，
 * 如果遇到第一个问题就中断，教师得改一处、传一次、再发现下一个问题。
 * 这里把每条问题都带上下标（第几份卷、第几题）收集起来，最后一次性返回，
 * 而且此时事务整体回滚，不会留下「导了一半」的半成品试卷。</p>
 *
 * <p><b>为什么一律建成草稿</b>：与「智能组卷」保持一致。导入的内容还没被教师
 * 检查过，直接发布就等于把未经审核的卷子推给学生；发布动作必须留在教师手里。</p>
 *
 * <p><b>分值与判分的口径</b>：题目在题库里有默认分值，但<b>卷面分值</b>以
 * {@code question_snapshot} 为准（与 {@link ExamRuleService} 和判分引擎一致）。
 * 导入时题目若带了 {@code score}，它既作为新建题的题库默认分，也作为本卷的卷面分。</p>
 */
@Slf4j
@Service
public class ExamImportService {

    /** 单次导入的题目总数上限：避免一个超大 payload 把库写满 */
    private static final int MAX_QUESTIONS_PER_IMPORT = 500;

    /** 错误信息最多回显多少条，避免一次刷屏 */
    private static final int MAX_ERRORS_SHOWN = 20;

    /** 新建题目未给分值时，按题型的默认分 */
    private static final Map<Integer, Integer> DEFAULT_SCORE_BY_TYPE =
            Map.of(0, 3, 1, 5, 2, 2, 3, 4, 4, 10);

    private static final Map<Integer, String> TYPE_NAME =
            Map.of(0, "单选题", 1, "多选题", 2, "判断题", 3, "填空题", 4, "简答题");

    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final ExamMapper examMapper;
    private final ObjectMapper objectMapper;

    public ExamImportService(QuestionBankMapper bankMapper,
                             QuestionMapper questionMapper,
                             ExamMapper examMapper,
                             ObjectMapper objectMapper) {
        this.bankMapper = bankMapper;
        this.questionMapper = questionMapper;
        this.examMapper = examMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 导入试卷。
     *
     * @param form   导入内容（单卷或多卷）
     * @param userId 操作人（教师/管理员），同时作为新建题库与题目的创建人
     * @return 导入结果摘要：{@code importedExams / createdQuestions / exams[]}
     */
    @Transactional
    public Map<String, Object> importPapers(ExamImportDTO form, Long userId) {
        if (form == null) {
            throw new BusinessException(400, "导入内容为空");
        }
        List<String> errors = new ArrayList<>();

        QuestionBank bank = resolveBank(form, userId, errors);
        List<ExamImportDTO.ImportExam> papers = collectPapers(form, errors);

        int questionTotal = 0;
        for (ExamImportDTO.ImportExam p : papers) {
            questionTotal += (p.getQuestions() == null ? 0 : p.getQuestions().size());
        }
        if (questionTotal == 0) {
            errors.add("导入内容里没有任何题目");
        } else if (questionTotal > MAX_QUESTIONS_PER_IMPORT) {
            errors.add("单次导入的题目数不能超过 " + MAX_QUESTIONS_PER_IMPORT
                    + " 道（当前 " + questionTotal + " 道）");
        }

        List<Map<String, Object>> created = new ArrayList<>();
        int createdQuestionCount = 0;

        // 题库解析不出来时后面每条都会跟着报错，先止住，错误信息才可读
        boolean canBuild = bank != null && questionTotal > 0
                && questionTotal <= MAX_QUESTIONS_PER_IMPORT;
        if (canBuild) {
            for (int i = 0; i < papers.size(); i++) {
                PaperResult result = buildPaper(papers.get(i), bank, userId,
                        "第 " + (i + 1) + " 份试卷", errors);
                if (result != null) {
                    created.add(result.summary());
                    createdQuestionCount += result.createdQuestions();
                }
            }
        }

        failIfAny(errors);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("importedExams", created.size());
        out.put("createdQuestions", createdQuestionCount);
        out.put("exams", created);
        log.info("试卷导入完成 teacherId={}, 试卷数={}, 新建题目={}",
                userId, created.size(), createdQuestionCount);
        return out;
    }

    // ==================== 题库 ====================

    private QuestionBank resolveBank(ExamImportDTO form, Long userId, List<String> errors) {
        if (form.getBankId() != null) {
            QuestionBank existing = bankMapper.selectById(form.getBankId());
            if (existing == null) {
                errors.add("指定的题库不存在（bankId=" + form.getBankId() + "）");
                return null;
            }
            return existing;
        }

        ExamImportDTO.BankSpec spec = form.getBank();
        if (spec == null || spec.getName() == null || spec.getName().isBlank()) {
            errors.add("请指定目标题库：用 bankId 指向已有题库，或用 bank.name 指定（不存在会自动新建）");
            return null;
        }

        String name = spec.getName().trim();
        List<QuestionBank> matched = bankMapper.selectList(
                new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getName, name));
        if (!matched.isEmpty()) {
            return matched.get(0);
        }

        QuestionBank created = new QuestionBank();
        created.setName(name);
        created.setSubject(spec.getSubject() == null || spec.getSubject().isBlank()
                ? name : spec.getSubject().trim());
        created.setDescription(spec.getDescription());
        created.setCreatedBy(userId);
        created.setCreatedAt(LocalDateTime.now());
        bankMapper.insert(created);
        log.info("导入时新建题库 id={}, name={}", created.getId(), name);
        return bankMapper.selectById(created.getId());
    }

    // ==================== 试卷 ====================

    private List<ExamImportDTO.ImportExam> collectPapers(ExamImportDTO form, List<String> errors) {
        List<ExamImportDTO.ImportExam> papers = new ArrayList<>();
        boolean hasExams = form.getExams() != null && !form.getExams().isEmpty();
        boolean hasQuestions = form.getQuestions() != null && !form.getQuestions().isEmpty();

        if (hasExams) {
            if (hasQuestions) {
                errors.add("questions 与 exams 不能同时使用：单卷导入用 title + questions，多卷导入用 exams[]");
                return papers;
            }
            papers.addAll(form.getExams());
            return papers;
        }

        ExamImportDTO.ImportExam one = new ExamImportDTO.ImportExam();
        one.setTitle(form.getTitle());
        one.setDescription(form.getDescription());
        one.setDuration(form.getDuration());
        one.setPassScore(form.getPassScore());
        one.setPaperType(form.getPaperType());
        one.setQuestions(form.getQuestions());
        papers.add(one);
        return papers;
    }

    private PaperResult buildPaper(ExamImportDTO.ImportExam paper, QuestionBank bank, Long userId,
                                   String where, List<String> errors) {
        if (paper == null) {
            errors.add(where + "：内容为空");
            return null;
        }
        String title = paper.getTitle() == null ? "" : paper.getTitle().trim();
        if (title.isEmpty()) {
            errors.add(where + "：title 不能为空");
            return null;
        }
        if (paper.getDuration() == null || paper.getDuration() <= 0) {
            errors.add(where + "「" + title + "」：duration 必须是大于 0 的分钟数");
            return null;
        }
        List<ExamImportDTO.ImportQuestion> items = paper.getQuestions();
        if (items == null || items.isEmpty()) {
            errors.add(where + "「" + title + "」：没有任何题目");
            return null;
        }

        List<QuestionSnapshot> snapshot = new ArrayList<>();
        Set<Long> usedQuestionIds = new LinkedHashSet<>();
        int totalScore = 0;
        int createdQuestions = 0;
        int errorsBefore = errors.size();

        for (int i = 0; i < items.size(); i++) {
            ExamImportDTO.ImportQuestion item = items.get(i);
            String tag = where + "「" + title + "」第 " + (i + 1) + " 题";
            if (item == null) {
                errors.add(tag + "：内容为空");
                continue;
            }

            if (item.getQuestionId() != null) {
                // 引用题库里已有的题目
                Question existing = questionMapper.selectById(item.getQuestionId());
                if (existing == null) {
                    errors.add(tag + "：引用的题目不存在（questionId=" + item.getQuestionId() + "）");
                    continue;
                }
                if (!Objects.equals(existing.getBankId(), bank.getId())) {
                    errors.add(tag + "：题目 " + existing.getId() + " 不属于目标题库「"
                            + bank.getName() + "」，不能跨题库组卷");
                    continue;
                }
                if (!usedQuestionIds.add(existing.getId())) {
                    errors.add(tag + "：题目 " + existing.getId() + " 在本卷中重复出现");
                    continue;
                }
                Integer score = item.getScore() != null ? item.getScore() : existing.getScore();
                if (score == null || score <= 0) {
                    errors.add(tag + "：分值必须是大于 0 的整数");
                    continue;
                }
                snapshot.add(new QuestionSnapshot(existing.getId(), score));
                totalScore += score;
            } else {
                // 新题：连同试卷一起入库
                Question built = buildQuestion(item, bank, userId, tag, errors);
                if (built == null) {
                    continue;
                }
                snapshot.add(new QuestionSnapshot(built.getId(), built.getScore()));
                totalScore += built.getScore();
                createdQuestions++;
            }
        }

        // 本卷任一处有错就不落库（事务最终统一回滚，避免半成品）
        if (errors.size() > errorsBefore) {
            return null;
        }
        if (snapshot.isEmpty()) {
            errors.add(where + "「" + title + "」：有效题目为 0");
            return null;
        }

        int passScore = paper.getPassScore() != null && paper.getPassScore() > 0
                ? paper.getPassScore() : (int) Math.ceil(totalScore * 0.6);
        if (passScore > totalScore) {
            errors.add(where + "「" + title + "」：及格分（" + passScore
                    + "）不能高于试卷总分（" + totalScore + "）");
            return null;
        }

        Exam exam = new Exam();
        exam.setTitle(title);
        exam.setDescription(paper.getDescription());
        exam.setBankId(bank.getId());
        exam.setDuration(paper.getDuration());
        exam.setTotalScore(totalScore);
        exam.setPassScore(passScore);
        // V4：试卷类型。留空按「模拟卷」—— 老模板（不含 paperType）导入出来的行为不变
        exam.setPaperType(paper.getPaperType() == null || paper.getPaperType().isBlank()
                ? "模拟卷" : paper.getPaperType().trim());
        exam.setStatus(0);            // 一律草稿
        exam.setCreatedBy(userId);
        // 导入的卷子是「题面固定」的，没有抽题规则，因此 rule_config 留空；
        // 但 question_snapshot 必须写 —— 取题与判分都只认它（与智能组卷一致），
        // 漏写会得到一份「题数为 0」的空卷，而接口返回值看起来一切正常。
        try {
            exam.setQuestionSnapshot(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            errors.add(where + "「" + title + "」：题目快照序列化失败");
            return null;
        }
        examMapper.insert(exam);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", exam.getId());
        summary.put("title", title);
        summary.put("questionCount", snapshot.size());
        summary.put("totalScore", totalScore);
        summary.put("passScore", passScore);
        summary.put("status", exam.getStatus());
        return new PaperResult(summary, createdQuestions);
    }

    // ==================== 题目 ====================

    private Question buildQuestion(ExamImportDTO.ImportQuestion item, QuestionBank bank, Long userId,
                                   String tag, List<String> errors) {
        Integer type = item.getType();
        if (type == null || type < 0 || type > 4) {
            errors.add(tag + "：type 必须是 0-4（0 单选 / 1 多选 / 2 判断 / 3 填空 / 4 简答）");
            return null;
        }
        String content = item.getContent() == null ? "" : item.getContent().trim();
        if (content.isEmpty()) {
            errors.add(tag + "：content 不能为空");
            return null;
        }
        String rawAnswer = item.getAnswer() == null ? "" : item.getAnswer().trim();
        if (rawAnswer.isEmpty()) {
            errors.add(tag + "：answer 不能为空");
            return null;
        }
        Integer difficulty = item.getDifficulty() == null ? 2 : item.getDifficulty();
        if (difficulty < 1 || difficulty > 3) {
            errors.add(tag + "：difficulty 必须是 1（简单）/ 2（中等）/ 3（困难）");
            return null;
        }

        NormalizedOptions opts = normalizeOptions(item.getOptions(), type, tag, errors);
        String answer = validateAnswer(type, rawAnswer, opts, tag, errors);
        if (answer == null) {
            return null;
        }

        Integer score = item.getScore() != null ? item.getScore() : DEFAULT_SCORE_BY_TYPE.get(type);
        if (score == null || score <= 0) {
            errors.add(tag + "：score 必须是大于 0 的整数");
            return null;
        }

        Question q = new Question();
        q.setBankId(bank.getId());
        q.setType(type);
        q.setDifficulty(difficulty);
        q.setContent(content);
        q.setOptions(opts == null ? null : opts.json());
        q.setAnswer(answer);
        q.setScore(score);
        q.setAnalysis(item.getAnalysis());
        // V4：知识点。空串落成 null（空串会变成一个「假的专题名」，见 QuestionController 同名处理）
        String category = item.getCategory() == null ? null : item.getCategory().trim();
        q.setCategory(category == null || category.isEmpty() ? null : category);
        q.setStatus(1);
        q.setCreatedBy(userId);
        q.setCreatedAt(LocalDateTime.now());
        questionMapper.insert(q);
        return questionMapper.selectById(q.getId());
    }

    /**
     * 校验并规范化答案。
     *
     * @return 规范化后的答案；校验不通过时返回 null（错误已写入 errors）
     */
    private String validateAnswer(int type, String rawAnswer, NormalizedOptions opts,
                                  String tag, List<String> errors) {
        if (type == 3 || type == 4) {
            // 填空/简答是主观题，答案由教师复核，不做格式约束
            return rawAnswer;
        }
        String upper = rawAnswer.toUpperCase();

        if (type == 2) {
            if (!upper.equals("A") && !upper.equals("B")) {
                errors.add(tag + "：判断题答案只能是 A（正确）或 B（错误），当前为「" + rawAnswer + "」");
                return null;
            }
            return upper;
        }

        // 单选 / 多选
        if (!upper.matches("[A-Z]+")) {
            errors.add(tag + "：选择题答案只能是字母（如 B、ABD），当前为「" + rawAnswer + "」");
            return null;
        }
        for (int i = 0; i < upper.length(); i++) {
            String label = String.valueOf(upper.charAt(i));
            if (!opts.labels().contains(label)) {
                errors.add(tag + "：答案「" + upper + "」里含未定义的选项「" + label
                        + "」（本题选项为 " + String.join("", opts.labels()) + "）");
                return null;
            }
        }
        if (type == 0 && upper.length() != 1) {
            errors.add(tag + "：单选题答案必须且只能有 1 个字母，当前为「" + upper + "」");
            return null;
        }
        if (type == 1 && upper.length() < 2) {
            errors.add(tag + "：多选题答案至少需要 2 个字母（本题选项为 "
                    + String.join("", opts.labels()) + "）");
            return null;
        }
        Set<Character> seen = new LinkedHashSet<>();
        for (char c : upper.toCharArray()) {
            if (!seen.add(c)) {
                errors.add(tag + "：答案「" + upper + "」里有重复字母");
                return null;
            }
        }
        // 统一按字母升序存储，与判分引擎的比较口径一致
        List<Character> chars = new ArrayList<>(seen);
        chars.sort(Character::compareTo);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    /**
     * 把三种写法的 options 统一成库里存的 {@code [{"label":"A","content":"…"}]}。
     *
     * <p>判断题不需要写选项，这里直接补上「正确 / 错误」；
     * 填空与简答没有选项，一律存 null。</p>
     */
    private NormalizedOptions normalizeOptions(Object raw, int type, String tag, List<String> errors) {
        if (type == 3 || type == 4) {
            return null;
        }
        if (type == 2) {
            Map<String, String> fixed = new LinkedHashMap<>();
            fixed.put("A", "正确");
            fixed.put("B", "错误");
            return new NormalizedOptions(optionsJson(List.of("A", "B"), fixed), fixed.keySet());
        }

        JsonNode node = objectMapper.valueToTree(raw);
        if (node == null || node.isNull()) {
            errors.add(tag + "：选择题必须提供 options");
            return null;
        }

        Map<String, String> byLabel = new TreeMap<>();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                byLabel.put(e.getKey().trim().toUpperCase(), e.getValue().asText(""));
            }
        } else if (node.isArray()) {
            for (JsonNode el : node) {
                if (el.isArray() && el.size() >= 2) {
                    byLabel.put(el.get(0).asText().trim().toUpperCase(), el.get(1).asText(""));
                } else if (el.isObject() && el.hasNonNull("label")) {
                    byLabel.put(el.get("label").asText().trim().toUpperCase(),
                            el.hasNonNull("content") ? el.get("content").asText("") : "");
                } else {
                    errors.add(tag + "：options 的元素既不是 [\"A\",\"内容\"]，"
                            + "也不是 {\"label\":\"A\",\"content\":\"内容\"}");
                }
            }
        } else {
            errors.add(tag + "：options 必须是对象（{\"A\":\"内容\"}）或数组");
            return null;
        }

        if (byLabel.size() < 2) {
            errors.add(tag + "：选择题至少需要 2 个选项，当前 " + byLabel.size() + " 个");
            return null;
        }
        List<String> labels = new ArrayList<>(byLabel.keySet());
        return new NormalizedOptions(optionsJson(labels, byLabel), byLabel.keySet());
    }

    private String optionsJson(List<String> labels, Map<String, String> contentByLabel) {
        List<Map<String, String>> list = new ArrayList<>();
        for (String label : labels) {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("label", label);
            o.put("content", contentByLabel.getOrDefault(label, ""));
            list.add(o);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BusinessException("选项序列化失败");
        }
    }

    private void failIfAny(List<String> errors) {
        if (errors.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("导入失败，共 ").append(errors.size()).append(" 个问题：\n");
        errors.stream().limit(MAX_ERRORS_SHOWN).forEach(e -> sb.append("· ").append(e).append('\n'));
        if (errors.size() > MAX_ERRORS_SHOWN) {
            sb.append("… 另有 ").append(errors.size() - MAX_ERRORS_SHOWN).append(" 个问题未显示");
        }
        throw new BusinessException(400, sb.toString().trim());
    }

    /** 规范化后的选项：json 用于入库，labels 用于校验答案 */
    private record NormalizedOptions(String json, Set<String> labels) { }

    /** 单份试卷的导入结果 */
    private record PaperResult(Map<String, Object> summary, int createdQuestions) { }
}