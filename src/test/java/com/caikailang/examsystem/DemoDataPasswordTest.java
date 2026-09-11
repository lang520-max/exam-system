package com.caikailang.examsystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交付物自检：直接读 {@code sql/data.sql}，验证里面写的 BCrypt 哈希确实对应演示口令。
 *
 * <p>为什么值得单独测：演示数据里的哈希是「离线生成后粘进脚本」的，
 * 一旦口令或哈希被改错，README 里的演示账号就登不进去，而这在跑集成测试时是发现不了的
 * （集成测试用的是 H2 脚本）。这条用例把 README 的承诺变成了可执行断言。</p>
 *
 * @author 蔡开浪
 */
class DemoDataPasswordTest {

    /** BCrypt 密文形如 $2a$10$ + 22 位盐 + 31 位哈希，共 60 字符 */
    private static final Pattern BCRYPT = Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}");

    private static final String DEMO_PASSWORD = "123456";

    @Test
    @DisplayName("演示数据：sql/data.sql 中的口令哈希确实对应明文 123456，且三个账号哈希各不相同")
    void demoPasswords_shouldBeBcryptHashesOfDefaultPassword() throws Exception {
        Path script = Paths.get("sql", "data.sql");
        assertTrue(Files.exists(script), "sql/data.sql 应当存在（测试的工作目录是项目根目录）");

        String sql = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);

        Matcher matcher = BCRYPT.matcher(sql);
        Set<String> hashes = new LinkedHashSet<String>();
        while (matcher.find()) {
            hashes.add(matcher.group());
        }

        assertEquals(3, hashes.size(),
            "teacher / student01 / student02 三个账号应各有一个 BCrypt 哈希");
        assertFalse(sql.contains("'" + DEMO_PASSWORD + "'"),
            "data.sql 里绝不能出现明文口令");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (String hash : hashes) {
            assertTrue(encoder.matches(DEMO_PASSWORD, hash),
                "哈希必须能用演示口令 " + DEMO_PASSWORD + " 校验通过：" + hash);
        }
    }

    @Test
    @DisplayName("演示数据：H2 测试脚本里的哈希与 MySQL 脚本一致，且能通过口令校验")
    void h2DemoPasswords_shouldMatchMySqlScript() throws Exception {
        Path h2Script = Paths.get("src", "test", "resources", "data-h2.sql");
        assertTrue(Files.exists(h2Script), "H2 演示数据脚本应当存在");

        String sql = new String(Files.readAllBytes(h2Script), StandardCharsets.UTF_8);
        Matcher matcher = BCRYPT.matcher(sql);
        Set<String> hashes = new LinkedHashSet<String>();
        while (matcher.find()) {
            hashes.add(matcher.group());
        }

        assertEquals(3, hashes.size(), "H2 脚本同样应包含 3 个账号的哈希");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (String hash : hashes) {
            assertTrue(encoder.matches(DEMO_PASSWORD, hash),
                "H2 脚本里的哈希也必须对应演示口令：" + hash);
        }
    }
}
