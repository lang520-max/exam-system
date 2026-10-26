package com.exam.websocket;

import com.exam.common.utils.JwtUtil;
import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WebSocket 多端互踢：<b>旧连接的迟到 onClose 不能误杀新连接</b>。
 *
 * <p>互踢的顺序是「先关旧连接、再把新 session 放进 SESSION_MAP」，
 * 而容器对旧连接的 {@code onClose} 回调发生在<b>另一个线程、通常更晚</b>。
 * 原实现里 onClose 无条件 {@code SESSION_MAP.remove(key)} + 取消倒计时任务，
 * 于是它删掉的是<b>新连接</b>：学生换设备 / 重连后推送很快停止，
 * 到点的 TIMEOUT 自动交卷通知也收不到 —— 只能靠前端本地计时兜着。</p>
 *
 * <p>这里直接驱动 handler 的 {@code onOpen / onClose}（不启容器），
 * 用 Mockito 造 Session，并断言两个包级可见的注册表。</p>
 */
@DisplayName("WebSocket 互踢 · 旧连接 onClose 不能误杀新连接")
class WebSocketKickTest {

    private static final String SECRET =
            "ws-kick-test-secret-key-must-be-at-least-256-bits-long-abcdef";
    private static final long STUDENT_ID = 5L;
    private static final long EXAM_ID = 1L;

    private final ExamWebSocketHandler handler = new ExamWebSocketHandler();
    private JwtUtil jwtUtil;

    @BeforeEach
    void wireStaticBeans() {
        jwtUtil = new JwtUtil(SECRET, 1_800_000L, 604_800_000L);
        ExamWebSocketHandler.setStaticBeans(jwtUtil, null);
        ExamWebSocketHandler.SESSION_MAP.clear();
        ExamWebSocketHandler.TASK_MAP.clear();
    }

    @AfterEach
    void clearRegistry() {
        ExamWebSocketHandler.SESSION_MAP.clear();
        ExamWebSocketHandler.TASK_MAP.clear();
    }

    /** 造一个可用的 session：token 合法、query 带远期 deadline（这样倒计时任务会持续推送） */
    private Session mockSession() throws IOException {
        String token = jwtUtil.generateAccessToken(STUDENT_ID, "student01", 2);
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getQueryString()).thenReturn(
                "token=" + token + "&deadline=" + (System.currentTimeMillis() + 3_600_000L));
        when(session.getUserProperties()).thenReturn(new HashMap<>());
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getBasicRemote()).thenReturn(basic);
        return session;
    }

    private String key() {
        return EXAM_ID + ":" + STUDENT_ID;
    }

    @Test
    @DisplayName("【关键】被踢掉的旧连接迟到 onClose 时，新连接仍在注册表里且倒计时未被取消")
    void lateOnCloseOfKickedSession_doesNotKillNewConnection() throws Exception {
        Session oldSession = mockSession();
        Session newSession = mockSession();

        handler.onOpen(oldSession, EXAM_ID, STUDENT_ID);
        assertThat(ExamWebSocketHandler.SESSION_MAP).containsEntry(key(), oldSession);

        // 同一考生换设备：新连接建立并踢掉旧的
        handler.onOpen(newSession, EXAM_ID, STUDENT_ID);
        assertThat(ExamWebSocketHandler.SESSION_MAP).containsEntry(key(), newSession);

        // 旧连接的任务句柄应当已经被取消（不能留孤儿任务一直推消息）
        Object oldTask = oldSession.getUserProperties().get("countdownTask");
        assertThat(oldTask).isInstanceOf(ScheduledFuture.class);
        assertThat(((ScheduledFuture<?>) oldTask).isCancelled()).isTrue();

        // 旧连接的 onClose 迟到了（真实环境下就是异步回调）
        handler.onClose(oldSession, EXAM_ID, STUDENT_ID);

        assertThat(ExamWebSocketHandler.SESSION_MAP)
                .as("旧连接的 onClose 绝不能把新连接从注册表里删掉")
                .containsEntry(key(), newSession);

        Object newTask = newSession.getUserProperties().get("countdownTask");
        assertThat(newTask).isInstanceOf(ScheduledFuture.class);
        assertThat(((ScheduledFuture<?>) newTask).isCancelled())
                .as("新连接的倒计时任务不能被旧连接取消")
                .isFalse();
    }

    @Test
    @DisplayName("正常关闭（没有互踢）时把自己清理干净，不留任务")
    void normalClose_cleansUpItsOwnTask() throws Exception {
        Session session = mockSession();
        handler.onOpen(session, EXAM_ID, STUDENT_ID);

        handler.onClose(session, EXAM_ID, STUDENT_ID);

        assertThat(ExamWebSocketHandler.SESSION_MAP).doesNotContainKey(key());
        assertThat(ExamWebSocketHandler.TASK_MAP).doesNotContainKey(key());
        ScheduledFuture<?> task = (ScheduledFuture<?>) session.getUserProperties().get("countdownTask");
        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("互踢时旧连接确实被要求关闭")
    void kick_closesOldSession() throws Exception {
        Session oldSession = mockSession();
        Session newSession = mockSession();

        handler.onOpen(oldSession, EXAM_ID, STUDENT_ID);
        handler.onOpen(newSession, EXAM_ID, STUDENT_ID);

        org.mockito.Mockito.verify(oldSession).close(any(CloseReason.class));
    }
}