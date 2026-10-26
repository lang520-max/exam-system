package com.exam.controller;

import com.exam.auth.JwtUser;
import com.exam.common.constant.RecordStatus;
import com.exam.common.result.Result;
import com.exam.dto.response.MyRecordVO;
import com.exam.service.ExamService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩统计面板
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ExamService examService;

    public DashboardController(ExamService examService) {
        this.examService = examService;
    }

    /**
     * 学生个人成绩概览：考了几场、已交卷几场、平均分、最高分、及格门数。
     *
     * <p><b>「已交卷」的口径是 {@code status IN (1, 2)}</b>（待判分 + 已出分），
     * 与 {@code ExamRecordMapper.statsByExamId} 完全一致。
     * 此前这里写的是 {@code status >= 2}，把 TIMEOUT(3)「超时未交卷」也算成了已交卷 ——
     * 同一批数据在「我的成绩」页与教师端统计里给出两个不同的完成人数，
     * 而且学生「挂机超时」反倒会让自己看起来更像完成了考试。</p>
     *
     * <p>平均分 / 最高分 / 及格门数同样只在这批「真交过卷」的记录里取，
     * 并排除分数为 null 的（待判分阶段还没分数，把它按 0 分算会把平均分整体压低）；
     * 只用「有分」的记录也避免了空指针与 {@code orElse(0)} 混淆「0 分」与「没数据」。</p>
     *
     * <p>返回字段名保持不变：学生端「我的考试 / 我的成绩」页直接按这些键渲染。</p>
     */
    @GetMapping("/my-stats")
    public Result<Map<String, Object>> myStats(@AuthenticationPrincipal JwtUser user) {
        List<MyRecordVO> records = examService.getMyRecords(user.getId());

        List<MyRecordVO> submitted = records.stream()
                .filter(r -> r.getStatus() != null
                        && (r.getStatus() == RecordStatus.PENDING_GRADE
                            || r.getStatus() == RecordStatus.GRADED))
                .toList();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExams", records.size());
        stats.put("submittedCount", submitted.size());
        stats.put("avgScore", submitted.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(MyRecordVO::getScore)
                .average().orElse(0));
        stats.put("maxScore", submitted.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(MyRecordVO::getScore)
                .max().orElse(0));
        // 及格门数也限定在已交卷记录里：passed 只有在出分后才非 null，
        // 但超时记录若将来带上分数，用同一个集合筛选才不会又出现两套口径
        stats.put("passedCount", submitted.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPassed())).count());
        return Result.success(stats);
    }
}