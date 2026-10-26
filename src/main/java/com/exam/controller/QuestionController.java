/*
 * Recovered from ECS app.jar (build 2026-09-19) using CFR 0.152.
 * The original Java source comments and formatting were not present in the deployed artifact.
 */

package com.exam.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.auth.JwtUser;
import com.exam.common.exception.BusinessException;
import com.exam.common.result.PageResult;
import com.exam.common.result.Result;
import com.exam.dto.CategoryCount;
import com.exam.dto.QuestionSnapshot;
import com.exam.dto.request.BankForm;
import com.exam.dto.request.QuestionForm;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.entity.QuestionBank;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.QuestionBankMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.service.ExamRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api"})
public class QuestionController {
    private static final int DEFAULT_DIFFICULTY = 2;
    private static final int DEFAULT_STATUS = 1;
    private static final int MAX_TITLES_SHOWN = 1;
    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final ExamMapper examMapper;
    private final ExamRuleService examRuleService;
    private final ObjectMapper objectMapper;

    public QuestionController(QuestionBankMapper bankMapper, QuestionMapper questionMapper, ExamMapper examMapper, ExamRuleService examRuleService, ObjectMapper objectMapper) {
        this.bankMapper = bankMapper;
        this.questionMapper = questionMapper;
        this.examMapper = examMapper;
        this.examRuleService = examRuleService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value={"/banks"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<QuestionBank>> listBanks() {
        return Result.success(this.bankMapper.selectList(null));
    }

    @PostMapping(value={"/banks"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<QuestionBank> createBank(@Valid @RequestBody BankForm form, @AuthenticationPrincipal JwtUser user) {
        QuestionBank bank = new QuestionBank();
        bank.setName(form.getName().trim());
        bank.setSubject(form.getSubject());
        bank.setDescription(form.getDescription());
        bank.setCreatedBy(user.getId());
        bank.setCreatedAt(LocalDateTime.now());
        this.bankMapper.insert(bank);
        return Result.success(((QuestionBank)this.bankMapper.selectById((Serializable)bank.getId())));
    }

    @DeleteMapping(value={"/banks/{id}"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Void> deleteBank(@PathVariable Long id, @AuthenticationPrincipal JwtUser user) {
        QuestionBank bank = (QuestionBank)this.bankMapper.selectById((Serializable)id);
        if (bank == null) {
            throw new BusinessException(404, "\u9898\u5e93\u4e0d\u5b58\u5728");
        }
        this.assertOwnership(bank.getCreatedBy(), user, "\u53ea\u80fd\u5220\u9664\u81ea\u5df1\u521b\u5efa\u7684\u9898\u5e93");
        long count = this.questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getBankId, id));
        if (count > 0L) {
            throw new BusinessException("\u8be5\u9898\u5e93\u4e0b\u8fd8\u6709 " + count + " \u9053\u9898\u76ee\uff0c\u8bf7\u5148\u5220\u9664\u9898\u76ee");
        }
        this.bankMapper.deleteById((Serializable)id);
        return Result.success();
    }

    @GetMapping(value={"/questions"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<PageResult<Question>> listQuestions(@RequestParam(required=false) Long bankId, @RequestParam(required=false) Integer type, @RequestParam(required=false) Integer difficulty, @RequestParam(required=false) String category, @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        String trimmed = keyword == null ? "" : keyword.trim();
        String kw = trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
        String pattern = kw == null ? null : "%" + kw + "%";
        String categoryValue = category == null || category.isBlank() ? null : category.trim();
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(bankId != null, Question::getBankId, bankId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .eq(categoryValue != null, Question::getCategory, categoryValue)
                .and(kw != null, w -> w.apply("LOWER(content) LIKE {0}", pattern)
                        .or().apply("LOWER(options) LIKE {0}", pattern)
                        .or().apply("LOWER(analysis) LIKE {0}", pattern)
                        .or().apply("LOWER(answer) LIKE {0}", pattern)
                        .or().apply("LOWER(category) LIKE {0}", pattern))
                .orderByDesc(Question::getCreatedAt);
        Page<Question> p = new Page<Question>(page, Math.min(size, 50));
        this.questionMapper.selectPage(p, wrapper);
        return Result.success(new PageResult<Question>(p.getRecords(), p.getTotal(), page, size));
    }

    @GetMapping(value={"/questions/{id}"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Question> getQuestion(@PathVariable Long id) {
        Question q = (Question)this.questionMapper.selectById((Serializable)id);
        if (q == null) {
            throw new BusinessException(404, "\u9898\u76ee\u4e0d\u5b58\u5728");
        }
        return Result.success(q);
    }

    @PostMapping(value={"/questions"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Question> createQuestion(@Valid @RequestBody QuestionForm form, @AuthenticationPrincipal JwtUser user) {
        Question question = new Question();
        this.applyForm(question, form);
        question.setCreatedBy(user.getId());
        question.setCreatedAt(LocalDateTime.now());
        this.questionMapper.insert(question);
        return Result.success(((Question)this.questionMapper.selectById((Serializable)question.getId())));
    }

    @PutMapping(value={"/questions/{id}"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Question> updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionForm form, @AuthenticationPrincipal JwtUser user) {
        Question exists = (Question)this.questionMapper.selectById((Serializable)id);
        if (exists == null) {
            throw new BusinessException(404, "\u9898\u76ee\u4e0d\u5b58\u5728");
        }
        this.assertOwnership(exists.getCreatedBy(), user, "\u53ea\u80fd\u4fee\u6539\u81ea\u5df1\u521b\u5efa\u7684\u9898\u76ee");
        this.assertBankExists(form.getBankId());
        NormalizedQuestion normalized = this.normalize(form);
        LambdaUpdateWrapper<Question> update = new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, id)
                .set(Question::getBankId, form.getBankId())
                .set(Question::getType, form.getType())
                .set(Question::getDifficulty, form.getDifficulty() == null ? DEFAULT_DIFFICULTY : form.getDifficulty())
                .set(Question::getContent, form.getContent().trim())
                .set(Question::getOptions, normalized.options())
                .set(Question::getAnswer, normalized.answer())
                .set(Question::getScore, form.getScore())
                .set(Question::getAnalysis, form.getAnalysis())
                .set(Question::getCategory, this.normalizeCategory(form.getCategory()))
                .set(Question::getStatus, form.getStatus() == null ? DEFAULT_STATUS : form.getStatus())
                .set(Question::getUpdatedAt, LocalDateTime.now());
        this.questionMapper.update(null, update);
        return Result.success(((Question)this.questionMapper.selectById((Serializable)id)));
    }

    @DeleteMapping(value={"/questions/{id}"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Void> deleteQuestion(@PathVariable Long id, @AuthenticationPrincipal JwtUser user) {
        Question exists = (Question)this.questionMapper.selectById((Serializable)id);
        if (exists == null) {
            throw new BusinessException(404, "\u9898\u76ee\u4e0d\u5b58\u5728");
        }
        this.assertOwnership(exists.getCreatedBy(), user, "\u53ea\u80fd\u5220\u9664\u81ea\u5df1\u521b\u5efa\u7684\u9898\u76ee");
        this.assertNotReferencedByExams(id);
        this.questionMapper.deleteById((Serializable)id);
        return Result.success();
    }

    @GetMapping(value={"/questions/subjects"})
    @PreAuthorize(value="hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<String>> listSubjects() {
        List<QuestionBank> banks = this.bankMapper.selectList(
                new LambdaQueryWrapper<QuestionBank>()
                        .select(QuestionBank::getSubject)
                        .isNotNull(QuestionBank::getSubject)
                        .groupBy(QuestionBank::getSubject));
        return Result.success(banks.stream().map(QuestionBank::getSubject).toList());
    }
    private void assertOwnership(Long createdBy, JwtUser user, String message) {
        if (user == null) {
            throw new BusinessException(401, "\u672a\u767b\u5f55");
        }
        if (user.isAdmin()) {
            return;
        }
        if (createdBy == null || !createdBy.equals(user.getId())) {
            throw new BusinessException(403, message);
        }
    }

    private void assertNotReferencedByExams(Long questionId) {
        List<Exam> exams = this.examMapper.selectList(
                new LambdaQueryWrapper<Exam>()
                        .select(Exam::getId, Exam::getTitle, Exam::getQuestionSnapshot));
        List<String> titles = new ArrayList<String>();
        for (Exam exam : exams) {
            for (QuestionSnapshot item : this.examRuleService.parseSnapshot(exam.getQuestionSnapshot())) {
                if (questionId.equals(item.getQuestionId())) {
                    titles.add(exam.getTitle() == null || exam.getTitle().isBlank()
                            ? "（无标题试卷 id=" + exam.getId() + "）"
                            : exam.getTitle());
                    break;
                }
            }
        }
        if (titles.isEmpty()) {
            return;
        }
        List<String> shown = titles.subList(0, Math.min(MAX_TITLES_SHOWN, titles.size()));
        String suffix = titles.size() > shown.size() ? " 等" : "";
        throw new BusinessException(400,
                "该题已被 " + titles.size() + " 份试卷引用（" + String.join("、", shown) + suffix + "），不能删除");
    }
    private void applyForm(Question question, QuestionForm form) {
        this.assertBankExists(form.getBankId());
        NormalizedQuestion normalized = this.normalize(form);
        question.setBankId(form.getBankId());
        question.setType(form.getType());
        question.setDifficulty(Integer.valueOf(form.getDifficulty() == null ? 2 : form.getDifficulty()));
        question.setContent(form.getContent().trim());
        question.setOptions(normalized.options());
        question.setAnswer(normalized.answer());
        question.setScore(form.getScore());
        question.setAnalysis(form.getAnalysis());
        question.setCategory(this.normalizeCategory(form.getCategory()));
        question.setStatus(Integer.valueOf(form.getStatus() == null ? 1 : form.getStatus()));
    }

    private String normalizeCategory(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return v.isEmpty() ? null : v;
    }

    private void assertBankExists(Long bankId) {
        if (bankId == null || this.bankMapper.selectById((Serializable)bankId) == null) {
            throw new BusinessException(404, "\u9898\u5e93\u4e0d\u5b58\u5728");
        }
    }

    private NormalizedQuestion normalize(QuestionForm form) {
        String answer;
        int type = form.getType();
        String string = answer = form.getAnswer() == null ? "" : form.getAnswer().trim();
        if (type == 3 || type == 4) {
            return new NormalizedQuestion(null, answer);
        }
        if (type == 2) {
            String upper = answer.toUpperCase();
            if (!upper.equals("A") && !upper.equals("B")) {
                throw new BusinessException(400, "\u5224\u65ad\u9898\u7b54\u6848\u53ea\u80fd\u662f A\uff08\u6b63\u786e\uff09\u6216 B\uff08\u9519\u8bef\uff09\uff0c\u5f53\u524d\u4e3a\u300c" + answer + "\u300d");
            }
            return new NormalizedQuestion(this.judgeOptionsJson(), upper);
        }
        Map<String, String> byLabel = this.parseOptions(form.getOptions());
        if (byLabel.size() < 2) {
            throw new BusinessException(400, "\u9009\u62e9\u9898\u81f3\u5c11\u9700\u8981 2 \u4e2a\u9009\u9879\uff0c\u5f53\u524d " + byLabel.size() + " \u4e2a");
        }
        String upper = answer.toUpperCase();
        if (!upper.matches("[A-Z]+")) {
            throw new BusinessException(400, "\u9009\u62e9\u9898\u7b54\u6848\u53ea\u80fd\u662f\u5b57\u6bcd\uff08\u5982 B\u3001ABD\uff09\uff0c\u5f53\u524d\u4e3a\u300c" + answer + "\u300d");
        }
        for (int i = 0; i < upper.length(); ++i) {
            String label = String.valueOf(upper.charAt(i));
            if (byLabel.containsKey(label)) continue;
            throw new BusinessException(400, "\u7b54\u6848\u300c" + upper + "\u300d\u91cc\u542b\u672a\u5b9a\u4e49\u7684\u9009\u9879\u300c" + (String)label + "\u300d\uff08\u672c\u9898\u9009\u9879\u4e3a " + String.join((CharSequence)"", byLabel.keySet()) + "\uff09");
        }
        if (type == 0 && upper.length() != 1) {
            throw new BusinessException(400, "\u5355\u9009\u9898\u7b54\u6848\u5fc5\u987b\u4e14\u53ea\u80fd\u6709 1 \u4e2a\u5b57\u6bcd\uff0c\u5f53\u524d\u4e3a\u300c" + upper + "\u300d");
        }
        if (type == 1 && upper.length() < 2) {
            throw new BusinessException(400, "\u591a\u9009\u9898\u7b54\u6848\u81f3\u5c11\u9700\u8981 2 \u4e2a\u5b57\u6bcd\uff08\u672c\u9898\u9009\u9879\u4e3a " + String.join((CharSequence)"", byLabel.keySet()) + "\uff09");
        }
        LinkedHashSet<Character> seen = new LinkedHashSet<Character>();
        for (char c : upper.toCharArray()) {
            if (seen.add(Character.valueOf(c))) continue;
            throw new BusinessException(400, "\u7b54\u6848\u300c" + upper + "\u300d\u91cc\u6709\u91cd\u590d\u5b57\u6bcd");
        }
        ArrayList<Character> chars = new ArrayList<Character>(seen);
        chars.sort(Character::compareTo);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return new NormalizedQuestion(this.optionsJson(byLabel), sb.toString());
    }

    private Map<String, String> parseOptions(String raw) {
        JsonNode node;
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(400, "\u9009\u62e9\u9898\u5fc5\u987b\u63d0\u4f9b\u9009\u9879");
        }
        try {
            node = this.objectMapper.readTree(raw);
        }
        catch (Exception e) {
            throw new BusinessException(400, "\u9009\u9879\u4e0d\u662f\u5408\u6cd5\u7684 JSON");
        }
        if (node == null || node.isNull()) {
            throw new BusinessException(400, "\u9009\u62e9\u9898\u5fc5\u987b\u63d0\u4f9b\u9009\u9879");
        }
        TreeMap<String, String> byLabel = new TreeMap<String, String>();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                byLabel.put(((String)e.getKey()).trim().toUpperCase(), ((JsonNode)e.getValue()).asText(""));
            }
        } else if (node.isArray()) {
            for (JsonNode el : node) {
                if (el.isArray() && el.size() >= 2) {
                    byLabel.put(el.get(0).asText().trim().toUpperCase(), el.get(1).asText(""));
                    continue;
                }
                if (el.isObject() && el.hasNonNull("label")) {
                    byLabel.put(el.get("label").asText().trim().toUpperCase(), el.hasNonNull("content") ? el.get("content").asText("") : "");
                    continue;
                }
                throw new BusinessException(400, "\u9009\u9879\u7684\u5143\u7d20\u65e2\u4e0d\u662f [\"A\",\"\u5185\u5bb9\"]\uff0c\u4e5f\u4e0d\u662f {\"label\":\"A\",\"content\":\"\u5185\u5bb9\"}");
            }
        } else {
            throw new BusinessException(400, "\u9009\u9879\u5fc5\u987b\u662f\u5bf9\u8c61\uff08{\"A\":\"\u5185\u5bb9\"}\uff09\u6216\u6570\u7ec4");
        }
        return byLabel;
    }

    private String optionsJson(Map<String, String> byLabel) {
        List<LinkedHashMap<String, String>> list = new ArrayList<LinkedHashMap<String, String>>();
        byLabel.forEach((label, content) -> {
            LinkedHashMap<String, String> o = new LinkedHashMap<String, String>();
            o.put("label", (String)label);
            o.put("content", content == null ? "" : content);
            list.add(o);
        });
        try {
            return this.objectMapper.writeValueAsString(list);
        }
        catch (Exception e) {
            throw new BusinessException("\u9009\u9879\u5e8f\u5217\u5316\u5931\u8d25");
        }
    }

    private String judgeOptionsJson() {
        LinkedHashMap<String, String> fixed = new LinkedHashMap<String, String>();
        fixed.put("A", "\u6b63\u786e");
        fixed.put("B", "\u9519\u8bef");
        return this.optionsJson(fixed);
    }

    private record NormalizedQuestion(String options, String answer) {
    }
}
