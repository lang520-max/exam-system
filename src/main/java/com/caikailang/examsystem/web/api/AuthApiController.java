package com.caikailang.examsystem.web.api;

import com.caikailang.examsystem.common.Result;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.LoginForm;
import com.caikailang.examsystem.dto.RegisterForm;
import com.caikailang.examsystem.security.AuthInterceptor;
import com.caikailang.examsystem.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;

/**
 * 认证接口：登录、注册、登出、当前用户。
 *
 * <p>登录成功后把用户写入 {@code HttpSession}，后续所有接口的身份都取自 Session，
 * 前端无法通过传参冒充他人。</p>
 *
 * @author 蔡开浪
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService userService;

    public AuthApiController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录：校验通过后<b>重建</b> Session，防止会话固定攻击（Session Fixation）。
     *
     * <p>若沿用登录前的 sessionId，攻击者可以先诱导受害者使用一个已知的 sessionId，
     * 待其登录后再用同一个 ID 直接接管会话。所以这里先让旧 Session 失效，
     * 再创建新 Session 写入登录态。</p>
     */
    @PostMapping("/login")
    public Result<User> login(@Valid @RequestBody LoginForm form, HttpServletRequest request) {
        User user = userService.login(form);

        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_USER, user);

        return Result.ok(user);
    }

    /** 注册：只创建学生账号；注册后不自动登录，需重新登录一次 */
    @PostMapping("/register")
    public Result<User> register(@Valid @RequestBody RegisterForm form) {
        return Result.ok(userService.register(form));
    }

    /** 登出：清空 Session */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Result.ok();
    }

    /** 当前登录用户，未登录时 data 为 null（供前端初始化导航栏） */
    @GetMapping("/me")
    public Result<User> me(HttpSession session) {
        User current = AuthInterceptor.currentUser(session);
        if (current == null) {
            return Result.ok(null);
        }
        // 回库读一次：Session 里保存的只是登录那一刻的快照，
        // 昵称或角色被管理员改过之后，这里返回的才是最新值
        return Result.ok(userService.findById(current.getId()));
    }
}
