package com.caikailang.examsystem.web;

import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.security.AuthInterceptor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;

/**
 * 公共页面：首页、登录页、注册页。
 *
 * <p>页面控制器与 REST 控制器分开：前者返回模板名由 Thymeleaf 渲染，
 * 后者返回 {@code Result} JSON。混在一起会让异常处理的语义互相干扰。</p>
 *
 * @author lang520-max
 */
@Controller
public class PageController {

    /** 首页：已登录直接跳到对应工作台，未登录展示落地页 */
    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        User user = AuthInterceptor.currentUser(session);
        if (user != null) {
            return user.isTeacher() ? "redirect:/teacher/questions" : "redirect:/student/exams";
        }
        model.addAttribute("user", null);
        return "index";
    }

    @GetMapping("/login")
    public String login(HttpSession session, Model model) {
        User user = AuthInterceptor.currentUser(session);
        if (user != null) {
            return user.isTeacher() ? "redirect:/teacher/questions" : "redirect:/student/exams";
        }
        model.addAttribute("user", null);
        return "login";
    }

    @GetMapping("/register")
    public String register(HttpSession session, Model model) {
        model.addAttribute("user", AuthInterceptor.currentUser(session));
        return "register";
    }
}
