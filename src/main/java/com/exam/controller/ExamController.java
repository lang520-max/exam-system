package com.exam.controller;

import com.exam.aspect.Idempotent;
import com.exam.auth.JwtUser;
import com.exam.common.constant.ExamStatus;
import com.exam.common.exception.BusinessException;
import com.exam.common.result.Result;
import com.exam.dto.request.AnswerDraftDTO;
import com.exam.dto.request.ExamGenerateDTO;
import com.exam.dto.request.ExamImportDTO;
import com.exam.dto.request.ExamReviewDTO;
import com.exam.dto.request.ExamSubmitDTO;
import com.exam.dto.response.ExamResultVO;
import com.exam.dto.response.ExamReviewVO;
import com.exam.dto.response.ExamStatsVO;
import com.exam.dto.response.ExamVO;
import com.exam.dto.response.QuestionVO;
import com.exam.entity.Exam;
import com.exam.entity.ExamRecord;
import com.exam.mapper.ExamMapper;
import com.exam.service.ExamImportService;
import com.exam.service.ExamReviewService;
import com.exam.service.ExamRuleService;
import com.exam.service.ExamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 考试核心接口
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamMapper examMapper;
    private final ExamRuleService examRuleService;
    private final ExamService examService;
    private final ExamReviewService examReviewService;
    private final ExamImportService examImportService;

    public ExamController(ExamMapper examMapper,
                          ExamRuleService examRuleService,
                          ExamService examService,
                          ExamReviewService examReviewService,
                          ExamImportService examImportService) {
        this.examMapper = examMapper;
        this.examRuleService = examRuleService;
        this.examService = examService;
        this.examReviewService = examReviewService;
        this.examImportService = examImportService;
    }

    // ==================== 考试管理（教师） ====================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Exam> createExam(@Valid @RequestBody ExamGenerateDTO form,
                                    @AuthenticationPrincipal JwtUser user) {
        return Result.success(examRuleService.generateExam(form, user.getId()));
    }

    /**
     * JSON 导入试卷。
     *
     * <p>一次可导入一份或多份，题目既可以引用题库已有题目，也可以随卷新建。
     * 导入的试卷一律是<b>草稿</b>，需要教师在列表里手动发布。</p>
     *
     * <p>校验不通过时返回 400，并把每个问题带上下标（第几份卷、第几题）
     * 一次性返回，而不是遇到第一个错误就中断。</p>
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> importExams(@RequestBody ExamImportDTO form,
                                                    @AuthenticationPrincipal JwtUser user) {
        return Result.success(examImportService.importPapers(form, user.getId()));
    }

    @GetMapping
    public Result<List<ExamVO>> listExams(@RequestParam(required = false) Integer status,
                                          @AuthenticationPrincipal JwtUser user) {
        // 教师/管理员看全量（含草稿）；学生只能看已发布的。
        // 此前这个方法对所有人返回全部试卷，连 status=0 的草稿也能被学生读到。
        boolean staff = user != null && user.isStaff();

        LambdaQueryWrapper<Exam> qw = new LambdaQueryWrapper<Exam>()
                .orderByDesc(Exam::getCreatedAt);

        if (status != null) {
            if (!staff && status < 1) {
                // 学生主动请求草稿列表 -> 返回空，而不是把草稿给出去
                return Result.success(Collections.emptyList());
            }
            qw.eq(Exam::getStatus, status);
        } else if (!staff) {
            qw.ge(Exam::getStatus, 1);
        }

        // 统一转 ExamVO：列表本来也不展示 rule_config / question_snapshot，
        // 顺便保证任何角色都不会从列表接口拿到卷面快照。
        List<ExamVO> list = examMapper.selectList(qw).stream()
                .map(ExamVO::from)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<Object> getExam(@PathVariable Long id, @AuthenticationPrincipal JwtUser user) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        boolean staff = user != null && user.isStaff();
        if (!staff) {
            // 未发布试卷对学生等同于「不存在」：返回 404 而不是 403，
            // 避免用状态码泄露「这里确实有一场还没公开的考试」
            if (!exam.isPublished()) {
                throw new BusinessException(404, "考试不存在");
            }
            // 学生视角剔除 question_snapshot / rule_config，防止开考前拿到卷面构成
            return Result.success(ExamVO.from(exam));
        }
        return Result.success(exam);
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Exam> publishExam(@PathVariable Long id, @AuthenticationPrincipal JwtUser user) {
        Exam exam = requireOwnedExam(id, user);

        // 状态机：只允许「草稿 → 进行中」。
        // 此前这里直接 setStatus(1)，没有任何前置判断，于是可以把
        // 「已结束」的考试重新 publish 回「进行中」—— 学生又能进入一场本该结束的考试。
        if (!Integer.valueOf(ExamStatus.DRAFT).equals(exam.getStatus())) {
            throw new BusinessException(409, Integer.valueOf(ExamStatus.FINISHED).equals(exam.getStatus())
                    ? "已结束的考试不能重新发布，请另建一场"
                    : "该考试已经发布过了");
        }

        exam.setStatus(ExamStatus.IN_PROGRESS);
        if (exam.getStartTime() == null) {
            // 没有预设开始时间就按「发布即开始」处理：
            // 保持原有的「发布后学生立刻能考」行为，同时让时间窗字段真正有值。
            exam.setStartTime(java.time.LocalDateTime.now());
        }
        examMapper.updateById(exam);
        return Result.success(examMapper.selectById(id));
    }

    @PutMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Exam> endExam(@PathVariable Long id, @AuthenticationPrincipal JwtUser user) {
        Exam exam = requireOwnedExam(id, user);

        // 状态机：只允许「进行中 → 已结束」
        if (!Integer.valueOf(ExamStatus.IN_PROGRESS).equals(exam.getStatus())) {
            throw new BusinessException(409, Integer.valueOf(ExamStatus.DRAFT).equals(exam.getStatus())
                    ? "草稿尚未发布，无需结束"
                    : "该考试已经结束了");
        }

        exam.setStatus(ExamStatus.FINISHED);
        // 记下真正结束的时刻：时间窗字段不再是摆设，
        // 也让「什么时候关的这场考试」有据可查（到点自动结束的任务不写这里，
        // 因为它用的是预设的 end_time）。
        exam.setEndTime(java.time.LocalDateTime.now());
        examMapper.updateById(exam);
        return Result.success(examMapper.selectById(id));
    }

    /**
     * 取考试并校验<b>归属</b>。
     *
     * <p>本项目按「多教师共用」定位：写操作只能落在自己创建的考试上，
     * 管理员放行。此前只校验「是不是教师」，教师 A 可以发布 / 结束教师 B 的考试。</p>
     *
     * <p>读操作（列表 / 统计）刻意不按创建人过滤：同一院系的教师互看考试
     * 是有意的取舍，写权限才是真正需要隔离的地方。</p>
     */
    private Exam requireOwnedExam(Long id, JwtUser user) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        boolean admin = user != null && Integer.valueOf(0).equals(user.getRole());
        boolean mine = user != null && exam.getCreatedBy() != null
                && exam.getCreatedBy().equals(user.getId());
        if (!admin && !mine) {
            throw new BusinessException(403, "只能操作自己创建的考试");
        }
        return exam;
    }

    // ==================== 考试流程（学生） ====================

    @PostMapping("/{examId}/start")
    public Result<ExamRecord> startExam(@PathVariable Long examId,
                                         @AuthenticationPrincipal JwtUser user,
                                         HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return Result.success(examService.startExam(examId, user.getId(), ip));
    }

    @GetMapping("/records/{recordId}/questions")
    public Result<List<QuestionVO>> getQuestions(@PathVariable Long recordId,
                                                  @AuthenticationPrincipal JwtUser user) {
        return Result.success(examService.getExamQuestions(recordId, user.getId()));
    }

    /**
     * 暂存答案（答题过程中前端自动调用）。
     *
     * <p>只写作答文本、不改考试状态、不触发判分；重复调用幂等。
     * 前端只传有变化的题即可。</p>
     */
    @PostMapping("/records/{recordId}/draft")
    public Result<Map<String, Object>> saveDraftAnswers(@PathVariable Long recordId,
                                                         @RequestBody AnswerDraftDTO form,
                                                         @AuthenticationPrincipal JwtUser user) {
        return Result.success(examService.saveDraftAnswers(recordId, user.getId(), form.getAnswers()));
    }

    /**
     * 读取暂存答案 —— 刷新页面 / 断线重连后回填答题卡，避免「答了 45 分钟一刷新就没了」。
     */
    @GetMapping("/records/{recordId}/draft")
    public Result<Map<String, Object>> getDraftAnswers(@PathVariable Long recordId,
                                                        @AuthenticationPrincipal JwtUser user) {
        return Result.success(examService.getDraftAnswers(recordId, user.getId()));
    }

    @PostMapping("/submit")
    @Idempotent
    public Result<Map<String, Object>> submit(@Valid @RequestBody ExamSubmitDTO form,
                                               @AuthenticationPrincipal JwtUser user,
                                               @RequestHeader("X-Request-Id") String requestId) {
        return Result.success(examService.submitExam(form, user.getId(), requestId));
    }

    @GetMapping("/records/{recordId}/result")
    public Result<ExamResultVO> getResult(@PathVariable Long recordId,
                                           @AuthenticationPrincipal JwtUser user) {
        return Result.success(examService.getResult(recordId, user.getId()));
    }

    @GetMapping("/my")
    public Result<List<com.exam.dto.response.MyRecordVO>> myRecords(@AuthenticationPrincipal JwtUser user) {
        return Result.success(examService.getMyRecords(user.getId()));
    }

    // ==================== 教师批改 ====================

    /**
     * 批改视图：某场考试下所有考生的逐题作答。
     *
     * <p>学生看不到别人的卷子（{@code getResult} 会校验归属），
     * 所以教师需要这个独立入口。</p>
     */
    @GetMapping("/{examId}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<ExamReviewVO> reviewSheet(@PathVariable Long examId) {
        return Result.success(examReviewService.getReviewSheet(examId));
    }

    /**
     * 提交批改结果（可批量）。
     *
     * <p>教师是评分的最终权威：这里可以改主观题、客观题，
     * 也可以覆盖 AI 已经给出的分数。</p>
     */
    @PutMapping("/answers/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> reviewAnswers(@Valid @RequestBody ExamReviewDTO form,
                                                      @AuthenticationPrincipal JwtUser user) {
        // 带 isAdmin：教师只能批改自己创建的考试，管理员放行（M4 归属校验）
        return Result.success(examReviewService.reviewAnswers(form, user.getId(), user.isAdmin()));
    }

    // ==================== 统计 ====================

    @GetMapping("/{examId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<ExamStatsVO> getStats(@PathVariable Long examId) {
        return Result.success(examService.getStats(examId));
    }
}