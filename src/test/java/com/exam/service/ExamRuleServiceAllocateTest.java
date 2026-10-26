package com.exam.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 难度名额分配算法单元测试。
 *
 * <p>{@link ExamRuleService#allocate} 是静态纯函数：给定「要抽几道」和
 * 「三个难度各有多少库存」，算出每个难度该抽几道。
 * 把它抽成纯函数就是为了能在这里穷举边界情况，
 * 而不必为了验证一次借调逻辑去造一整个题库。</p>
 */
@DisplayName("智能组卷 · 难度名额分配")
class ExamRuleServiceAllocateTest {

    @Test
    @DisplayName("库存充足时按 5:3:2 分配")
    void splitsByRatioWhenStockIsEnough() {
        int[] quota = ExamRuleService.allocate(10, 100, 100, 100);
        assertThat(quota).containsExactly(5, 3, 2);
    }

    @Test
    @DisplayName("分配结果之和恒等于要抽的数量")
    void totalAlwaysMatchesRequestedCount() {
        for (int count = 1; count <= 30; count++) {
            int[] quota = ExamRuleService.allocate(count, 100, 100, 100);
            assertThat(quota[0] + quota[1] + quota[2])
                    .as("count=%d 时三个难度之和应等于 count", count)
                    .isEqualTo(count);
        }
    }

    @Test
    @DisplayName("困难题库存为 0 时，名额全部借给简单与中等")
    void borrowsFromOthersWhenHardIsEmpty() {
        int[] quota = ExamRuleService.allocate(10, 100, 100, 0);
        assertThat(quota[2]).isZero();
        assertThat(quota[0] + quota[1]).isEqualTo(10);
    }

    @Test
    @DisplayName("简单题库存不足时，缺口转给中等")
    void borrowsFromMediumWhenEasyIsShort() {
        // 要 10 道：初值 5/3/2，简单只有 1 道 → 中等补 4 道
        int[] quota = ExamRuleService.allocate(10, 1, 100, 100);
        assertThat(quota[0]).isEqualTo(1);
        assertThat(quota[1]).isEqualTo(7);
        assertThat(quota[2]).isEqualTo(2);
    }

    @Test
    @DisplayName("三个难度都恰好够用时不借调")
    void keepsQuotaWhenExactlyEnough() {
        int[] quota = ExamRuleService.allocate(10, 5, 3, 2);
        assertThat(quota).containsExactly(5, 3, 2);
    }

    @Test
    @DisplayName("总库存不足时，只能抽到全部库存")
    void capsAtTotalStockWhenNotEnoughOverall() {
        // 要 10 道，总共只有 4 道
        int[] quota = ExamRuleService.allocate(10, 2, 1, 1);
        assertThat(quota[0] + quota[1] + quota[2]).isEqualTo(4);
    }

    @Test
    @DisplayName("题库完全为空时三个难度都返回 0")
    void returnsZeroWhenBankIsEmpty() {
        assertThat(ExamRuleService.allocate(10, 0, 0, 0)).containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("要抽 0 道或负数时不分配任何名额")
    void returnsZeroForNonPositiveCount() {
        assertThat(ExamRuleService.allocate(0, 100, 100, 100)).containsExactly(0, 0, 0);
        assertThat(ExamRuleService.allocate(-5, 100, 100, 100)).containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("只抽 1 道时命中简单题")
    void picksEasyForSingleQuestion() {
        int[] quota = ExamRuleService.allocate(1, 100, 100, 100);
        assertThat(quota[0] + quota[1] + quota[2]).isEqualTo(1);
        assertThat(quota[0]).isEqualTo(1);
    }

    @Test
    @DisplayName("任何难度都不返回负数")
    void neverReturnsNegativeQuota() {
        // 穷举一批极端库存组合，确认不会因为借调减法出现负数
        int[] stocks = {0, 1, 2, 3, 5, 10};
        for (int easy : stocks) {
            for (int medium : stocks) {
                for (int hard : stocks) {
                    for (int count = 1; count <= 12; count++) {
                        int[] quota = ExamRuleService.allocate(count, easy, medium, hard);
                        assertThat(quota[0]).as("easy<0 (stock=%d,%d,%d count=%d)", easy, medium, hard, count).isNotNegative();
                        assertThat(quota[1]).as("medium<0 (stock=%d,%d,%d count=%d)", easy, medium, hard, count).isNotNegative();
                        assertThat(quota[2]).as("hard<0 (stock=%d,%d,%d count=%d)", easy, medium, hard, count).isNotNegative();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("分配数量不超过各自库存")
    void neverExceedsAvailableStock() {
        int[] stocks = {0, 1, 2, 3, 5, 10};
        for (int easy : stocks) {
            for (int medium : stocks) {
                for (int hard : stocks) {
                    int[] quota = ExamRuleService.allocate(10, easy, medium, hard);
                    assertThat(quota[0]).as("easy 超库存 stock=%d", easy).isLessThanOrEqualTo(easy);
                    assertThat(quota[1]).as("medium 超库存 stock=%d", medium).isLessThanOrEqualTo(medium);
                    assertThat(quota[2]).as("hard 超库存 stock=%d", hard).isLessThanOrEqualTo(hard);
                }
            }
        }
    }
}
