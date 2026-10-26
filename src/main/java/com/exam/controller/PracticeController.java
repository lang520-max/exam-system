package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.dto.CategoryCount;
import com.exam.dto.request.PracticeCheckForm;
import com.exam.dto.response.PracticeBankVO;
import com.exam.dto.response.PracticeResultVO;
import com.exam.dto.response.QuestionVO;
import com.exam.service.PracticeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自由练习接口。
 *
 * <p>学生自选题库 / 题型 / 难度 / 数量，逐题作答并立即看答案解析；
 * **不写 exam_record**，因此不影响成绩、及格率与统计（练习就是练习）。</p>
 *
 * <p>权限：登录即可（学生为主，教师也能用它自测）。故意<b>不加</b>
 * {@code @PreAuthorize} —— SecurityConfig 的兜底是 {@code anyRequest().authenticated()}，
 * 这里要的正是「任何已登录用户」。</p>
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /** 可练习的题库（id / 名称 / 科目 / 可用题量）。学生看得到，所以独立于教师专属的 /api/banks。 */
    @GetMapping("/banks")
    public Result<List<PracticeBankVO>> banks() {
        return Result.success(practiceService.listBanks());
    }

    /**
     * 抽题：随机 count 道，**不含答案与解析**（提交后才给）。
     * 题库 / 题型 / 难度 / **知识点**都可选，不传就是「全部」。
     */
    @GetMapping("/questions")
    public Result<List<QuestionVO>> questions(
            @RequestParam(required = false) Long bankId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int count) {
        return Result.success(practiceService.sample(bankId, type, difficulty, category, count));
    }

    /**
     * 可练习的知识点（含题量）——练习页「专题」下拉的数据源。
     *
     * <p>学生也要能选专题（「语法练习」「一元函数积分学专项」就是这么做的），
     * 所以这个接口对学生开放；返回的只有知识点名与题量，没有任何题目内容。</p>
     */
    @GetMapping("/categories")
    public Result<List<CategoryCount>> categories(@RequestParam(required = false) Long bankId) {
        return Result.success(practiceService.listCategories(bankId));
    }

    /** 对答案：提交一道题的作答，返回是否正确 + 参考答案 + 解析。 */
    @PostMapping("/check")
    public Result<PracticeResultVO> check(@Valid @RequestBody PracticeCheckForm form) {
        return Result.success(practiceService.check(form.getQuestionId(), form.getAnswer()));
    }
}