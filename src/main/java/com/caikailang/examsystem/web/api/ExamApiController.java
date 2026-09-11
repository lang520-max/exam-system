package com.caikailang.examsystem.web.api;

import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.common.Result;
import com.caikailang.examsystem.domain.ExamAnswer;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.dto.ExamQuestionView;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.SubmitForm;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.security.RequireLogin;
import com.caikailang.examsystem.service.ExamPaperService;
import com.caikailang.examsystem.service.ExamService;
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
 * 考试接口（登录后可用，具体角色限制在 Service 层）。
 *
 * <p>所有涉及「我是谁」的参数都不从请求里读：开考、答题、交卷一律使用
 * Session 中的登录用户，前端无法通过改传 studentId 去看别人的答卷。</p>
 *
 * @author 蔡开浪
 */
@RestController
@RequestMapping("/api/exams")
@RequireLogin
public class ExamApiController {

    private final ExamService examService;
    private final ExamPaperService paperService;

    public ExamApiController(ExamService examService, ExamPaperService paperService) {
        this.examService = examService;
        this.paperService = paperService;
    }

    /** 学生端：可参加的考试列表（含自己在这份卷子上的进度） */
    @GetMapping
    public Result<PageResult<ExamPaper>> available(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                   HttpSession session) {
        Long studentId = AuthInterceptor.requireUser(session).getId();
        return Result.ok(paperService.listPublishedForStudent(studentId, pageNum, pageSize));
    }

    /** 开始考试：生成考试记录并记录开始时间；已开考未交卷则返回原记录继续答题 */
    @PostMapping("/{paperId}/start")
    public Result<ExamRecord> start(@PathVariable("paperId") Long paperId, HttpSession session) {
        return Result.ok(examService.startExam(paperId, AuthInterceptor.requireUser(session)));
    }

    /** 取考试记录详情（含截止时间戳，供前端倒计时） */
    @GetMapping("/records/{recordId}")
    public Result<ExamRecord> record(@PathVariable("recordId") Long recordId, HttpSession session) {
        return Result.ok(examService.detail(recordId, AuthInterceptor.requireUser(session)));
    }

    /** 取试卷题目（不含正确答案） */
    @GetMapping("/records/{recordId}/questions")
    public Result<List<ExamQuestionView>> questions(@PathVariable("recordId") Long recordId, HttpSession session) {
        return Result.ok(examService.getExamQuestions(recordId, AuthInterceptor.requireUser(session)));
    }

    /** 交卷并自动判分（重复提交会被拒绝） */
    @PostMapping("/submit")
    public Result<ExamRecord> submit(@Valid @RequestBody SubmitForm form, HttpSession session) {
        return Result.ok(examService.submit(form, AuthInterceptor.requireUser(session)));
    }

    /** 学生：我的考试记录 */
    @GetMapping("/my")
    public Result<PageResult<ExamRecord>> my(ExamRecordQuery query, HttpSession session) {
        return Result.ok(examService.myRecords(query, AuthInterceptor.requireUser(session)));
    }

    /** 答卷回顾：每道题的作答、正确答案与得分 */
    @GetMapping("/records/{recordId}/review")
    public Result<List<ExamAnswer>> review(@PathVariable("recordId") Long recordId, HttpSession session) {
        return Result.ok(examService.review(recordId, AuthInterceptor.requireUser(session)));
    }

    /** 错题回顾：只返回答错的题 */
    @GetMapping("/records/{recordId}/wrong")
    public Result<List<ExamAnswer>> wrong(@PathVariable("recordId") Long recordId, HttpSession session) {
        return Result.ok(examService.wrongAnswers(recordId, AuthInterceptor.requireUser(session)));
    }
}
