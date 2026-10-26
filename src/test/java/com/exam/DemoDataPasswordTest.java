package com.exam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 演示数据自检 —— 把「README 说 teacher1 / 123456 能登录」变成可执行的断言。
 *
 * <p><b>为什么需要这个测试</b>：种子脚本里存的是 BCrypt 哈希，
 * 而哈希是随机盐生成的，肉眼完全看不出它对应哪个口令。
 * 一旦有人手工粘贴了一个来路不明的哈希串（本项目就踩过这个坑：
 * 三个演示账号的哈希其实不是 {@code 123456} 的哈希，
 * 测试全绿、数据库也建得好好的，但演示账号根本登不进去），
 * 只有真的拿口令去 {@code matches()} 一次才能发现。</p>
 *
 * <p>同时反查「脚本里不能出现明文口令」，避免有人图省事直接写明文。</p>
 */
@DisplayName("演示数据自检")
class DemoDataPasswordTest {

    /** 演示口令，与 README 中的说明必须一致 */
    private static final String DEMO_PASSWORD = "123456";

    /** BCrypt 哈希形如 $2a$10$ + 53 个字符 */
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    /** 需要同时校验的三份种子脚本 */
    private static final List<String> SEED_FILES = List.of(
            "sql/data.sql",
            "src/main/resources/data-h2.sql",
            "src/test/resources/data-test.sql"
    );

    @Test
    @DisplayName("三份种子脚本里的每个 BCrypt 哈希都能用演示口令 123456 校验通过")
    void everySeedHashMatchesDemoPassword() throws IOException {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        for (String file : SEED_FILES) {
            List<String> hashes = extractHashes(file);
            assertThat(hashes)
                    .as("%s 应当包含演示账号的 BCrypt 哈希", file)
                    .isNotEmpty();

            for (String hash : hashes) {
                assertThat(encoder.matches(DEMO_PASSWORD, hash))
                        .as("%s 中的哈希 %s 无法用演示口令 %s 校验通过"
                                + "（演示账号会登不进去）", file, hash, DEMO_PASSWORD)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("演示账号的 BCrypt 哈希互不相同（证明随机盐生效）")
    void seedHashesAreDistinct() throws IOException {
        for (String file : SEED_FILES) {
            List<String> hashes = extractHashes(file);
            assertThat(hashes)
                    .as("%s 中每个账号应使用各自独立的哈希（BCrypt 随机盐）", file)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("种子脚本中不出现明文口令")
    void seedFilesContainNoPlaintextPassword() throws IOException {
        for (String file : SEED_FILES) {
            String content = read(file);
            // 排除注释行（说明文字里会写「密码均为 123456」）
            String withoutComments = content.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(withoutComments)
                    .as("%s 的 SQL 语句里不应出现明文口令", file)
                    .doesNotContain("'" + DEMO_PASSWORD + "'");
        }
    }

    private List<String> extractHashes(String file) throws IOException {
        Matcher matcher = BCRYPT.matcher(read(file));
        List<String> hashes = new ArrayList<>();
        while (matcher.find()) {
            hashes.add(matcher.group());
        }
        return hashes;
    }

    private String read(String file) throws IOException {
        Path path = Path.of(file);
        assertThat(Files.exists(path)).as("种子脚本不存在: %s", path.toAbsolutePath()).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}