package com.caikailang.examsystem.web;

import com.caikailang.examsystem.common.PageResult;
import com.caikailang.examsystem.domain.ExamPaper;
import com.caikailang.examsystem.domain.Question;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.ExamRecordQuery;
import com.caikailang.examsystem.dto.QuestionQuery;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.security.RequireAdmin;
import com.caikailang.examsystem.service.ExamPaperService;
import com.caikailang.examsystem.service.ExamService;
import com.caikailang.examsystem.service.QuestionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

/**
 * 教师端页面：题库管理、试卷管理、组卷、成绩统计。
 *
 * <p>类级 {@link RequireAdmin} 由拦截器保证「未登录跳登录页、非教师返回 403」。</p>
 *
 * @author lang520-max
 */
@Controller
@RequestMapping("/teacher")
@RequireAdmin
public class TeacherPageController {

    private final QuestionService questionService;
    private final ExamPaperService paperService;
    private final ExamService examService;

    public TeacherPageController(QuestionService questionService,
                                 ExamPaperService paperService,
                                 ExamService examService) {
        this.questionService = questionService;
        this.paperService = paperService;
        this.examService = examService;
    }

    @GetMapping({"", "/"})
    public String home() {
        return "redirect:/teacher/questions";
    }

    /** 题库管理：服务端渲染列表与分页，新增/编辑通过 JS 调 REST 接口 */
    @GetMapping("/questions")
    public String questions(QuestionQuery query, Model model, HttpSession session) {
        PageResult<Question> page = questionService.search(query);
        model.addAttribute("page", page);
        model.addAttribute("query", query);
        model.addAttribute("subjects", questionService.listSubjects());
        model.addAttribute("user", AuthInterceptor.currentUser(session));
        return "teacher/questions";
    }

    /** 试卷列表 */
    @GetMapping("/papers")
    public String papers(@RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                         Model model, HttpSession session) {
        model.addAttribute("page", paperService.list(status, null, pageNum, 20));
        model.addAttribute("status", status);
        model.addAttribute("user", AuthInterceptor.currentUser(session));
        return "teacher/papers";
    }

    /** 组卷页：左侧手动选题、右侧随机抽题规则 */
    @GetMapping("/paper/new")
    public String paperNew(Model model, HttpSession session) {
        model.addAttribute("subjects", questionService.listSubjects());
        model.addAttribute("user", AuthInterceptor.currentUser(session));
        return "teacher/paper-new";
    }

    /** 成绩统计：选一场考试，看成绩列表 + 平均分/及格率/最高最低分 */
    @GetMapping("/scores")
    public String scores(@RequestParam(value = "paperId", required = false) Long paperId,
                         @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                         Model model, HttpSession session) {
        User user = AuthInterceptor.currentUser(session);
        PageResult<ExamPaper> papers = paperService.list(null, null, 1, 50);
        model.addAttribute("papers", papers.getList());
        model.addAttribute("user", user);
        model.addAttribute("selectedPaperId", paperId);
        if (paperId != null) {
            ExamRecordQuery query = new ExamRecordQuery();
            query.setPaperId(paperId);
            query.setPageNum(pageNum);
            query.setPageSize(20);
            query.setSortBy("score");
            query.setSortDir("desc");
            model.addAttribute("records", examService.paperRecords(query, user));
            model.addAttribute("stats", examService.paperStats(paperId, user));
        }
        return "teacher/scores";
    }
}
