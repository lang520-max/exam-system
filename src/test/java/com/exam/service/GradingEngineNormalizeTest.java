package com.exam.service;

import com.exam.common.constant.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 判分规则单元测试 —— 不启动 Spring、不连数据库。
 *
 * <p>{@link GradingEngine#normalize} 是静态纯函数，判分正确性完全押在它身上，
 * 因此这里逐条把「同一个答案的不同写法」钉成断言。</p>
 */
@DisplayName("判分引擎 · 答案规范化")
class GradingEngineNormalizeTest {

    @Nested
    @DisplayName("单选题")
    class SingleChoice {

        @Test
        @DisplayName("标准答案 A 原样通过")
        void keepsPlainLetter() {
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "A")).isEqualTo("A");
        }

        @Test
        @DisplayName("忽略大小写")
        void ignoresCase() {
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "a")).isEqualTo("A");
        }

        @Test
        @DisplayName("去掉首尾空白")
        void trimsWhitespace() {
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "  B  ")).isEqualTo("B");
        }

        @Test
        @DisplayName("剥掉「A.」「A、」之类的修饰只留字母")
        void stripsDecoration() {
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "A.")).isEqualTo("A");
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "C、")).isEqualTo("C");
        }

        @Test
        @DisplayName("null 与空白串视为未作答")
        void blankBecomesNull() {
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, null)).isNull();
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "")).isNull();
            assertThat(GradingEngine.normalize(QuestionType.SINGLE, "   ")).isNull();
        }
    }

    @Nested
    @DisplayName("判断题")
    class Judge {

        @Test
        @DisplayName("「对/正确/是/T/TRUE/Y/YES/√」统一归一化为 A")
        void normalizesTrueVariants() {
            for (String variant : new String[]{"A", "T", "TRUE", "Y", "YES", "对", "正确", "是", "√"}) {
                assertThat(GradingEngine.normalize(QuestionType.JUDGE, variant))
                        .as("判断题「%s」应归一化为 A", variant)
                        .isEqualTo("A");
            }
        }

        @Test
        @DisplayName("「错/错误/否/F/FALSE/N/NO/×/X」统一归一化为 B")
        void normalizesFalseVariants() {
            for (String variant : new String[]{"B", "F", "FALSE", "N", "NO", "错", "错误", "否", "×", "X"}) {
                assertThat(GradingEngine.normalize(QuestionType.JUDGE, variant))
                        .as("判断题「%s」应归一化为 B", variant)
                        .isEqualTo("B");
            }
        }

        @Test
        @DisplayName("大小写混写同样被识别")
        void handlesMixedCase() {
            assertThat(GradingEngine.normalize(QuestionType.JUDGE, "true")).isEqualTo("A");
            assertThat(GradingEngine.normalize(QuestionType.JUDGE, "False")).isEqualTo("B");
        }

        @Test
        @DisplayName("无法识别的写法按未作答处理")
        void unknownBecomesNull() {
            assertThat(GradingEngine.normalize(QuestionType.JUDGE, "maybe")).isNull();
        }
    }

    @Nested
    @DisplayName("多选题")
    class MultipleChoice {

        @Test
        @DisplayName("选项顺序不影响结果：CBA 等价于 ABC")
        void orderDoesNotMatter() {
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "CBA")).isEqualTo("ABC");
        }

        @Test
        @DisplayName("忽略分隔符：\"C,A\" 等价于 \"AC\"")
        void ignoresSeparators() {
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "C,A")).isEqualTo("AC");
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "A C")).isEqualTo("AC");
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "A、C")).isEqualTo("AC");
        }

        @Test
        @DisplayName("重复勾选去重：AAB 等价于 AB")
        void deduplicatesRepeatedSelection() {
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "AAB")).isEqualTo("AB");
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "AAABBBCCC")).isEqualTo("ABC");
        }

        @Test
        @DisplayName("全小写同样被识别")
        void handlesLowercase() {
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "abd")).isEqualTo("ABD");
        }

        @Test
        @DisplayName("没有可用字母时视为未作答")
        void noLettersBecomesNull() {
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "123")).isNull();
            assertThat(GradingEngine.normalize(QuestionType.MULTIPLE, "-")).isNull();
        }
    }

    @Nested
    @DisplayName("填空题 / 简答题")
    class Subjective {

        @Test
        @DisplayName("主观题不做字母裁剪：中文答案原样保留")
        void keepsChineseText() {
            assertThat(GradingEngine.normalize(QuestionType.ESSAY, "封装 继承 多态")).isEqualTo("封装 继承 多态");
        }

        @Test
        @DisplayName("主观题仅去除首尾空白并转大写")
        void trimsAndUppercases() {
            assertThat(GradingEngine.normalize(QuestionType.FILL, "  jvm  ")).isEqualTo("JVM");
        }
    }
}
