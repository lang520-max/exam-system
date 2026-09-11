package com.caikailang.examsystem.web.api;

import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.common.Result;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.ExamPaperQuestion;
import com.caikailang.examsystem.dto.PaperForm;
import com.caikailang.examsystem.dto.RandomPaperForm;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.security.RequireAdmin;
import com.caikailang.examsystem.service.ExamPaperService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * 组卷接口（仅教师可用）。
 *
 * <p>对外只提供「创建草稿」与「发布/结束」两类写操作，
 * 试卷内容一旦发布就不再支持修改，保证已考过的学生成绩口径稳定。</p>
 *
 * @author lang520-max
 */
@RestController
@RequestMapping("/api/papers")
@RequireAdmin
public class PaperApiController {

    private final ExamPaperService paperService;

    public PaperApiController(ExamPaperService paperService) {
        this.paperService = paperService;
    }

    /** 试卷列表（教师端） */
    @GetMapping
    public Result<PageResult<ExamPaper>> list(@RequestParam(value = "status", required = false) String status,
                                              @RequestParam(value = "creatorId", required = false) Long creatorId,
                                              @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                              @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return Result.ok(paperService.list(status, creatorId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<ExamPaper> detail(@PathVariable("id") Long id) {
        return Result.ok(paperService.detail(id));
    }

    /** 试卷题目预览（教师可见正确答案） */
    @GetMapping("/{id}/questions")
    public Result<List<ExamPaperQuestion>> questions(@PathVariable("id") Long id) {
        paperService.detail(id);
        return Result.ok(paperService.listQuestions(id));
    }

    /** 手动选题组卷 */
    @PostMapping
    public Result<ExamPaper> create(@Valid @RequestBody PaperForm form, HttpSession session) {
        return Result.ok(paperService.createManual(form, AuthInterceptor.requireUser(session)));
    }

    /** 按「科目 + 题型 + 数量 + 每题分值」随机抽题组卷 */
    @PostMapping("/random")
    public Result<ExamPaper> createRandom(@Valid @RequestBody RandomPaperForm form, HttpSession session) {
        return Result.ok(paperService.createRandom(form, AuthInterceptor.requireUser(session)));
    }

    /** 发布考试：学生端立刻可以看到并参加 */
    @PostMapping("/{id}/publish")
    public Result<ExamPaper> publish(@PathVariable("id") Long id, HttpSession session) {
        return Result.ok(paperService.updateStatus(id, ExamPaper.STATUS_PUBLISHED,
            AuthInterceptor.requireUser(session)));
    }

    /** 结束考试：不再接受新的开考，历史成绩保留 */
    @PostMapping("/{id}/close")
    public Result<ExamPaper> close(@PathVariable("id") Long id, HttpSession session) {
        return Result.ok(paperService.updateStatus(id, ExamPaper.STATUS_CLOSED,
            AuthInterceptor.requireUser(session)));
    }

    /** 删除试卷：仅限还没有学生考过的试卷 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpSession session) {
        paperService.delete(id, AuthInterceptor.requireUser(session));
        return Result.ok();
    }
}
