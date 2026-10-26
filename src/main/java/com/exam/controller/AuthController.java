package com.exam.controller;

import com.exam.auth.JwtUser;
import com.exam.common.result.Result;
import com.exam.dto.request.LoginForm;
import com.exam.dto.request.PasswordForm;
import com.exam.dto.request.RegisterForm;
import com.exam.dto.response.LoginVO;
import com.exam.entity.User;
import com.exam.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginForm form) {
        return Result.success(authService.login(form));
    }

    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterForm form) {
        return Result.success(authService.register(form));
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        return Result.success(authService.refresh(refreshToken));
    }

    /**
     * 登出：把 Refresh Token 拉黑。
     *
     * <p>不要求 Authorization 头：持有 refresh token 本身就是凭证，
     * 与 {@code /refresh} 同一口径。幂等 —— 没带 token 也返回成功，
     * 前端只管清本地状态即可。</p>
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<User> me(@AuthenticationPrincipal JwtUser jwtUser) {
        return Result.success(authService.getCurrentUser(jwtUser));
    }

    /**
     * 自助修改密码（需登录）。
     *
     * <p>这是 {@code /api/auth/**} 里**唯一不 permitAll** 的接口（见 SecurityConfig）：
     * 改密码必须先证明「你是本人」，因此要带 access token；
     * 顺带带上 refresh token 是为了改完把它拉黑 —— 服务端会强制你重新登录。</p>
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordForm form,
                                       @AuthenticationPrincipal JwtUser jwtUser,
                                       @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        if (jwtUser == null || jwtUser.getId() == null) {
            throw new com.exam.common.exception.BusinessException(401, "请先登录");
        }
        authService.changePassword(jwtUser.getId(), form, refreshToken);
        return Result.success();
    }
}