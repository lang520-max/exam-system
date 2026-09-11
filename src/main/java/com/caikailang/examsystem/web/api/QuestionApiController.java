package com.caikailang.examsystem.web.api;

import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.common.Result;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.dto.QuestionForm;
import com.caikailang.examsystem.dto.QuestionQuery;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.security.RequireAdmin;
import com.caikailang.examsystem.service.QuestionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * 题库接口（全部仅教师可用，类级 {@link RequireAdmin} 由拦截器统一校验）。
 *
 * <p>控制器只做「取 Session 身份 → 调 Service → 包 Result」，不写业务规则：
 * 角色校验在拦截器与 Service 各做一次，形成双保险。</p>
 *
 * @author lang520-max
 */
@RestController
@RequestMapping("/api/questions")
@RequireAdmin
public class QuestionApiController {

    private final QuestionService questionService;

    public QuestionApiController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 分页检索题库：支持关键字、科目、题型、难度与白名单排序 */
    @GetMapping
    public Result<PageResult<Question>> search(QuestionQuery query) {
        return Result.ok(questionService.search(query));
    }

    /** 题库中已有的科目清单，供筛选下拉框使用 */
    @GetMapping("/subjects")
    public Result<List<String>> subjects() {
        return Result.ok(questionService.listSubjects());
    }

    @GetMapping("/{id}")
    public Result<Question> detail(@PathVariable("id") Long id) {
        return Result.ok(questionService.detail(id));
    }

    @PostMapping
    public Result<Question> create(@Valid @RequestBody QuestionForm form, HttpSession session) {
        return Result.ok(questionService.create(form, AuthInterceptor.requireUser(session)));
    }

    @PutMapping("/{id}")
    public Result<Question> update(@PathVariable("id") Long id,
                                   @Valid @RequestBody QuestionForm form,
                                   HttpSession session) {
        return Result.ok(questionService.update(id, form, AuthInterceptor.requireUser(session)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpSession session) {
        questionService.delete(id, AuthInterceptor.requireUser(session));
        return Result.ok();
    }
}
