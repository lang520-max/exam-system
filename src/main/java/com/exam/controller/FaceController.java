package com.exam.controller;

import com.exam.auth.JwtUser;
import com.exam.common.exception.BusinessException;
import com.exam.common.result.Result;
import com.exam.dto.request.FaceEnrollForm;
import com.exam.dto.request.FaceOverrideForm;
import com.exam.dto.request.FaceVerifyForm;
import com.exam.dto.response.FaceLogVO;
import com.exam.dto.response.FaceStatusVO;
import com.exam.dto.response.FaceVerifyResultVO;
import com.exam.mapper.FaceVerifyLogMapper;
import com.exam.service.FaceRecognitionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人脸识别接口。
 *
 * <p>权限边界：录入/核验/查状态一律只允许**操作自己**（userId 取自 token，不接受前端传参），
 * 放行与流水查询仅 ADMIN/TEACHER。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/face")
public class FaceController {

    private final FaceRecognitionService faceService;
    private final FaceVerifyLogMapper logMapper;

    public FaceController(FaceRecognitionService faceService, FaceVerifyLogMapper logMapper) {
        this.faceService = faceService;
        this.logMapper = logMapper;
    }

    /** 当前用户的人脸状态（录入页/开考前自检用；带 examId 时还包含该场考试的核验情况） */
    /**
     * 公开的人脸功能配置。
     *
     * <p>登录页（注册时要采集人脸）与考试列表（点「开始考试」前要核验）都需要先知道
     * 门禁到底有没有开 —— 但它们此时可能还没有 token。这里只返回开关/阈值这类
     * **不含任何用户数据**的信息，所以允许匿名访问。</p>
     */
    @GetMapping("/config")
    public Result<java.util.Map<String, Object>> config() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("enabled", faceService.isEnabled());
        m.put("threshold", faceService.getThreshold());
        m.put("maxRetries", faceService.getMaxRetries());
        m.put("passWindowSeconds", faceService.getPassWindowSeconds());
        return Result.success(m);
    }

    @GetMapping("/status")
    public Result<FaceStatusVO> status(@AuthenticationPrincipal JwtUser user,
                                       @RequestParam(required = false) Long examId) {
        requireLogin(user);
        return Result.success(faceService.status(user.getId(), examId));
    }

    /** 录入/更新本人人脸底片（注册后立即调用，或老账号自助补录） */
    @PostMapping("/enroll")
    public Result<FaceStatusVO> enroll(@Valid @RequestBody FaceEnrollForm form,
                                       @AuthenticationPrincipal JwtUser user,
                                       HttpServletRequest request) {
        requireLogin(user);
        return Result.success(faceService.enroll(user.getId(), form, request.getRemoteAddr()));
    }

    /** 开考前核验（学生本人） */
    @PostMapping("/verify")
    public Result<FaceVerifyResultVO> verify(@Valid @RequestBody FaceVerifyForm form,
                                             @AuthenticationPrincipal JwtUser user,
                                             HttpServletRequest request) {
        requireLogin(user);
        return Result.success(faceService.verify(user.getId(), form, request.getRemoteAddr()));
    }

    /** 教师/管理员人工放行（学生失败达上限、或人脸服务不可用时用） */
    @PostMapping("/override")
    public Result<Void> override(@Valid @RequestBody FaceOverrideForm form,
                                 @AuthenticationPrincipal JwtUser user,
                                 HttpServletRequest request) {
        requireStaff(user);
        faceService.override(form.getUserId(), form.getExamId(), user.getId(),
                form.getReason(), request.getRemoteAddr());
        return Result.success();
    }

    /** 某场考试的人脸核验流水（教师端查看 + 放行依据） */
    @GetMapping("/logs")
    public Result<List<FaceLogVO>> logs(@RequestParam Long examId,
                                        @RequestParam(defaultValue = "50") int limit,
                                        @AuthenticationPrincipal JwtUser user) {
        requireStaff(user);
        int capped = Math.min(Math.max(limit, 1), 200);
        return Result.success(logMapper.selectByExam(examId, capped));
    }

    private void requireLogin(JwtUser user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private void requireStaff(JwtUser user) {
        requireLogin(user);
        if (!user.isStaff()) {
            throw new BusinessException(403, "只有教师或管理员可以执行该操作");
        }
    }
}