package com.caikailang.examsystem.service;

import com.caikailang.examsystem.common.BusinessException;
import com.caikailang.examsystem.domain.User;
import com.caikailang.examsystem.dto.LoginForm;
import com.caikailang.examsystem.dto.RegisterForm;
import com.caikailang.examsystem.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务：登录、注册、查询。
 *
 * <p>密码一律用 BCrypt 存储与校验；返回给控制器的 {@link User} 都会先
 * {@link User#clearPassword()}，保证密码哈希不会进 Session、不会进 JSON 响应。</p>
 *
 * @author 蔡开浪
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录校验。
     *
     * <p>用户名不存在与密码错误返回<b>同一句</b>提示，避免攻击者用错误信息差异
     * 枚举出系统里存在哪些账号。</p>
     */
    public User login(LoginForm form) {
        User user = userMapper.findByUsername(form.getUsername());
        if (user == null || !passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        user.clearPassword();
        return user;
    }

    /**
     * 注册学生账号。
     *
     * <p>角色固定为 {@code STUDENT}：注册接口不接受前端传入 role，
     * 否则任何人都能自封教师（管理员），属于典型的纵向越权。</p>
     */
    @Transactional
    public User register(RegisterForm form) {
        if (userMapper.countByUsername(form.getUsername()) > 0) {
            throw new BusinessException("用户名「" + form.getUsername() + "」已存在，请换一个");
        }
        User user = new User();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setNickname(form.getNickname());
        user.setRole(User.ROLE_STUDENT);
        userMapper.insert(user);
        user.clearPassword();
        return user;
    }

    /** 按 ID 查用户（返回的对象已擦除密码） */
    public User findById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.clearPassword();
        return user;
    }
}
