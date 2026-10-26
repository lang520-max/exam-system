package com.exam.web;

import com.exam.common.exception.BusinessException;
import com.exam.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理的 <b>HTTP 状态码契约</b>。
 *
 * <p>用 standalone MockMvc 直接跑 advice，不启动 Spring 上下文：这里要验证的
 * 正是「注解有没有把真实状态码带出去」，与业务无关，跑得又快又聚焦。</p>
 *
 * <p>存在的意义是防回归：兜底的 {@code Exception} 分支曾经漏了
 * {@code @ResponseStatus}，Spring 于是返回 HTTP 200、只把 500 写在响应体里，
 * 于是 nginx / APM / 压测工具都把真故障统计成成功。</p>
 */
@DisplayName("全局异常处理 · HTTP 状态码")
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/dup")
        String dup() {
            throw new DuplicateKeyException("dup");
        }

        @GetMapping("/biz")
        String biz() {
            throw new BusinessException(404, "没有这个东西");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("未预期异常返回真实 HTTP 500（而不是 200 里塞 code=500）")
    void unexpectedException_returnsHttp500() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("唯一键冲突返回真实 HTTP 409")
    void duplicateKey_returnsHttp409() throws Exception {
        mockMvc.perform(get("/dup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("业务码映射成真实状态码（404 → 404）")
    void businessException_returnsMappedHttpStatus() throws Exception {
        mockMvc.perform(get("/biz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}