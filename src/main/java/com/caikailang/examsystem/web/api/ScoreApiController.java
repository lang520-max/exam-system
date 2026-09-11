package com.caikailang.examsystem.web.api;

import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.common.Result;
import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.ExamStats;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.security.RequireAdmin;
import com.caikailang.examsystem.security.RequireLogin;
import com.caikailang.examsystem.service.ExamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

/**
 * 成绩与统计接口。
 *
 * <p>学生只能看自己的（{@code /api/scores/my}，学生身份取自 Session），
 * 教师可以看任意一场考试的成绩与统计（{@code /api/scores/paper/**}）。</p>
 *
 * @author 蔡开浪
 */
@RestController
@RequestMapping("/api/scores")
public class ScoreApiController {

    private final ExamService examService;

    public ScoreApiController(ExamService examService) {
        this.examService = examService;
    }

    /** 学生：我的成绩列表（分页、可按状态筛选） */
    @GetMapping("/my")
    @RequireLogin
    public Result<PageResult<ExamRecord>> my(ExamRecordQuery query, HttpSession session) {
        return Result.ok(examService.myRecords(query, AuthInterceptor.requireUser(session)));
    }

    /** 学生：我的成绩概览（考试场次、平均分、最高分、及格率） */
    @GetMapping("/my/stats")
    @RequireLogin
    public Result<ExamStats> myStats(HttpSession session) {
        return Result.ok(examService.myStats(AuthInterceptor.requireUser(session)));
    }

    /** 教师：某场考试的成绩列表 */
    @GetMapping("/paper/{paperId}")
    @RequireAdmin
    public Result<PageResult<ExamRecord>> byPaper(@PathVariable("paperId") Long paperId,
                                                  ExamRecordQuery query,
                                                  HttpSession session) {
        query.setPaperId(paperId);
        return Result.ok(examService.paperRecords(query, AuthInterceptor.requireUser(session)));
    }

    /** 教师：某场考试的成绩统计（平均分 / 及格率 / 最高分 / 最低分） */
    @GetMapping("/paper/{paperId}/stats")
    @RequireAdmin
    public Result<ExamStats> paperStats(@PathVariable("paperId") Long paperId, HttpSession session) {
        return Result.ok(examService.paperStats(paperId, AuthInterceptor.requireUser(session)));
    }
}
