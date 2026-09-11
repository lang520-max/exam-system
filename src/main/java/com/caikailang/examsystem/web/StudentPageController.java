package com.caikailang.examsystem.web;

import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.ExamRecord;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.ExamQuestionView;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.security.RequireLogin;
import com.caikailang.examsystem.service.ExamPaperService;
import com.caikailang.examsystem.service.ExamService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * 学生端页面：考试列表、在线答题、我的成绩、错题回顾。
 *
 * <p>答题页用服务端渲染出题目（刷新不丢题），交卷与倒计时用少量原生 JS 调 REST 接口。</p>
 *
 * @author lang520-max
 */
@Controller
@RequestMapping("/student")
@RequireLogin
public class StudentPageController {

    private final ExamService examService;
    private final ExamPaperService paperService;

    public StudentPageController(ExamService examService, ExamPaperService paperService) {
        this.examService = examService;
        this.paperService = paperService;
    }

    @GetMapping({"", "/"})
    public String home() {
        return "redirect:/student/exams";
    }

    /** 可参加的考试列表 */
    @GetMapping("/exams")
    public String exams(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                        Model model, HttpSession session) {
        User user = AuthInterceptor.currentUser(session);
        PageResult<ExamPaper> page = paperService.listPublishedForStudent(user.getId(), pageNum, 20);
        model.addAttribute("page", page);
        model.addAttribute("user", user);
        return "student/exams";
    }

    /** 在线答题页；已交卷的记录自动跳到成绩回顾 */
    @GetMapping("/exam")
    public String exam(@RequestParam("recordId") Long recordId, Model model, HttpSession session) {
        User user = AuthInterceptor.currentUser(session);
        ExamRecord record = examService.detail(recordId, user);
        if (!record.isOngoing()) {
            return "redirect:/student/review?recordId=" + recordId;
        }
        List<ExamQuestionView> questions = examService.getExamQuestions(recordId, user);
        model.addAttribute("record", record);
        model.addAttribute("questions", questions);
        model.addAttribute("user", user);
        return "student/exam";
    }

    /** 我的成绩 */
    @GetMapping("/scores")
    public String scores(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                         Model model, HttpSession session) {
        User user = AuthInterceptor.currentUser(session);
        ExamRecordQuery query = new ExamRecordQuery();
        query.setPageNum(pageNum);
        query.setPageSize(20);
        PageResult<ExamRecord> page = examService.myRecords(query, user);
        model.addAttribute("page", page);
        model.addAttribute("stats", examService.myStats(user));
        model.addAttribute("user", user);
        return "student/scores";
    }

    /** 成绩详情与错题回顾 */
    @GetMapping("/review")
    public String review(@RequestParam("recordId") Long recordId, Model model, HttpSession session) {
        User user = AuthInterceptor.currentUser(session);
        ExamRecord record = examService.detail(recordId, user);
        if (record.isOngoing()) {
            // 还没交卷的记录没有成绩可看，直接送回答题页
            return "redirect:/student/exam?recordId=" + recordId;
        }
        model.addAttribute("record", record);
        model.addAttribute("answers", examService.review(recordId, user));
        model.addAttribute("wrongs", examService.wrongAnswers(recordId, user));
        model.addAttribute("user", user);
        return "student/review";
    }
}
