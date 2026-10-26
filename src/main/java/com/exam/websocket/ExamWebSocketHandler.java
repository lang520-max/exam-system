package com.exam.websocket;

import com.exam.common.utils.JwtUtil;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 考试倒计时处理器
 *
 * <p>核心功能：
 * <ol>
 *   <li>连接建立时校验 JWT Token</li>
 *   <li>每秒从 Redis 读取剩余时间并推送到客户端</li>
 *   <li>时间到 → 推送 TIMEOUT 消息通知自动交卷</li>
 *   <li>断线重连：通过 examId + userId 恢复原有会话</li>
 * </ol>
 *
 * <p>面试重点：
 * <ul>
 *   <li>Redis TTL 作为倒计时真相（单源），WebSocket 仅做推送通道</li>
 *   <li>断线重连后从 Redis 获取剩余时间，不重置计时</li>
 *   <li>多端登录互踢：相同 examId+userId 的新连接会关闭旧连接</li>
 * </ul>
 */
@Slf4j
@Component
@ServerEndpoint("/ws/exam/{examId}/{userId}")
public class ExamWebSocketHandler {

    private static JwtUtil jwtUtil;
    private static StringRedisTemplate redisTemplate;

    /**
     * 会话管理器：examId:userId → Session。
     *
     * <p>与 {@link #TASK_MAP} 一样是<b>包级可见</b>而不是 private：
     * 「互踢时旧连接的 onClose 不能误删新连接」这条行为只有在单元测试里
     * 直接断言这两个 map 才能验证（见 WebSocketKickTest）。</p>
     */
    static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();
    /** 每个会话的倒计时任务句柄：examId:userId → ScheduledFuture（包级可见，便于测试） */
    static final Map<String, ScheduledFuture<?>> TASK_MAP = new ConcurrentHashMap<>();

    /**
     * 全局共享的倒计时调度器。
     *
     * <p>原实现是每个 WebSocket 连接 `Executors.newSingleThreadScheduledExecutor()`，
     * 也就是<b>一个考生一个线程</b>：1000 人同时在线就是 1000 个线程，
     * 且每线程每秒打一次 Redis。对一个主打「高并发」的系统来说，
     * 这是最先垮掉的地方。</p>
     *
     * <p>改为固定大小的共享线程池：推送本身只是极轻的 IO 操作，
     * 用不着每连接一个线程。线程设为 daemon，容器关闭时不会被它拖住。</p>
     */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "exam-ws-countdown");
                t.setDaemon(true);
                return t;
            });

    private static final String TIMEOUT_KEY = "exam:timeout:%s:%s";

    /** 倒计时任务句柄挂在 session 上的属性名（用于「只取消属于自己的任务」） */
    private static final String TASK_PROP = "countdownTask";

    /**
     * 由于 WebSocket 不是 Spring 管理的，通过静态方法注入 Spring Bean
     */
    public static void setStaticBeans(JwtUtil jwtUtilBean, StringRedisTemplate redisTemplateBean) {
        jwtUtil = jwtUtilBean;
        redisTemplate = redisTemplateBean;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("examId") Long examId, @PathParam("userId") Long userId) {
        String token = getTokenFromSession(session);
        // 必须同时校验「签名有效」和「token 归属于这个 userId」
        if (!tokenBelongsTo(token, userId)) {
            closeSession(session, "Token 验证失败");
            return;
        }

        String sessionKey = examId + ":" + userId;

        // 关闭已有连接（互踢）
        Session oldSession = SESSION_MAP.get(sessionKey);
        if (oldSession != null && oldSession.isOpen()) {
            closeSession(oldSession, "新设备登录");
        }
        // 互踢残留：主动取消上一次登记的任务句柄。
        // 旧连接的 onClose 是异步回调（在别的线程、通常更晚才触发），
        // 不能指望它来收尾；否则旧任务会成为孤儿，一直往已关闭的会话推消息。
        ScheduledFuture<?> previous = TASK_MAP.remove(sessionKey);
        if (previous != null) {
            previous.cancel(false);
        }

        SESSION_MAP.put(sessionKey, session);
        log.info("WebSocket 连接建立: examId={}, userId={}", examId, userId);

        // 启动倒计时推送任务
        startCountdownTask(examId, userId, session, sessionKey);
    }

    @OnClose
    public void onClose(Session session, @PathParam("examId") Long examId, @PathParam("userId") Long userId) {
        String sessionKey = examId + ":" + userId;
        boolean removed = removeSessionIfOwner(sessionKey, session);
        stopOwnCountdownTask(session, sessionKey);
        log.info("WebSocket 连接关闭: examId={}, userId={}, 清理了会话={}", examId, userId, removed);
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("examId") Long examId, @PathParam("userId") Long userId) {
        log.error("WebSocket 错误: examId={}, userId={}", examId, userId, error);
        String sessionKey = examId + ":" + userId;
        removeSessionIfOwner(sessionKey, session);
        stopOwnCountdownTask(session, sessionKey);
    }

    @OnMessage
    public void onMessage(String message, Session session,
                          @PathParam("examId") Long examId, @PathParam("userId") Long userId) {
        // 客户端发来的消息，可用于心跳检测等
        if ("PING".equals(message)) {
            sendMessage(session, "PONG");
        }
    }

    /**
     * 启动倒计时推送任务 —— 每秒从 Redis 获取剩余时间并推送
     */
    private void startCountdownTask(Long examId, Long userId, Session session, String sessionKey) {
        String timeoutKey = String.format(TIMEOUT_KEY, examId, userId);

        // Redis 不可用时的降级依据：前端把「交卷截止时刻（epoch 毫秒）」带在 query 上。
        // 这样 dev / 测试档（没有 Redis）也能演示出真正的服务端倒计时，
        // 而不是让前端自己 setInterval 猜时间。
        Long deadline = parseDeadline(session);

        ScheduledFuture<?> future = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                if (!session.isOpen()) {
                    stopOwnCountdownTask(session, sessionKey);
                    return;
                }

                long remaining = resolveRemainingSeconds(timeoutKey, deadline);

                if (remaining <= 0) {
                    // 倒计时结束 → 推送 TIMEOUT
                    sendMessage(session, "{\"type\":\"TIMEOUT\",\"remaining\":0}");
                    stopOwnCountdownTask(session, sessionKey);
                    return;
                }

                // 每秒推送剩余时间
                sendMessage(session, "{\"type\":\"COUNTDOWN\",\"remaining\":" + remaining + "}");

            } catch (Exception e) {
                log.error("倒计时推送异常: examId={}, userId={}", examId, userId, e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        // 句柄登记两处：
        //  - TASK_MAP（按 examId:userId）用于「同样的会话键再来一条连接」时收尾；
        //  - session.userProperties（按连接）保证旧连接的 onClose 只取消自己那一个，
        //    不会误停新连接的倒计时（互踢时两者的 key 完全相同）。
        TASK_MAP.put(sessionKey, future);
        session.getUserProperties().put(TASK_PROP, future);
    }

    /**
     * 计算剩余秒数。
     *
     * <p><b>只调一次 getExpire</b>：原实现先 {@code GET} 判断 key 是否存在、
     * 再 {@code getExpire} 取 TTL，是一次多余的网络往返（每秒 × 每个考生）。
     * {@code getExpire} 本身就能区分三种情况：正数 = 剩余秒数，
     * -1 = key 存在但无 TTL，-2 = key 不存在（已过期或从未设置）。</p>
     */
    private long resolveRemainingSeconds(String timeoutKey, Long deadline) {
        if (redisTemplate != null) {
            // 生产路径：Redis TTL 是剩余时间的唯一真相
            try {
                Long ttl = redisTemplate.getExpire(timeoutKey, TimeUnit.SECONDS);
                return ttl != null && ttl > 0 ? ttl : 0;
            } catch (Exception e) {
                // Redis 抖动不应让倒计时推送直接抛异常刷屏
                log.warn("读取 Redis 倒计时失败，本条按到点处理: {}", e.getMessage());
                return 0;
            }
        }
        if (deadline != null) {
            // 降级路径：按截止时刻算剩余秒数
            return Math.max(0, (deadline - System.currentTimeMillis()) / 1000L);
        }
        // 既没有 Redis 也没有截止时刻，无法确定剩余时间，按到点处理
        return 0;
    }

    /**
     * 校验 token 有效，<b>并且确实属于路径里的 userId</b>。
     *
     * <p><b>只校验签名是不够的</b>。userId 来自 URL 路径
     * （{@code /ws/exam/{examId}/{userId}}），若只验证「这是个合法 JWT」，
     * 任何已登录用户都能填<b>别人的</b> userId 连上来。而 {@link #onOpen}
     * 里有「多端登录互踢」逻辑——会关闭同一个 {@code examId:userId} 的旧连接——
     * 于是任何学生都能把同学正在考试的连接踢掉，反复重连即可全程干扰。</p>
     *
     * <p>同时要求 token 必须是 <b>access</b> 类型：refresh token 有效期 7 天，
     * 若能拿来建 WebSocket，「access 短命」的设计前提同样被绕过。</p>
     */
    static boolean tokenBelongsTo(String token, Long userId) {
        if (token == null || userId == null || jwtUtil == null) {
            return false;
        }
        if (!jwtUtil.validate(token) || !jwtUtil.isAccessToken(token)) {
            return false;
        }
        Long tokenUserId = jwtUtil.getUserId(token);
        return tokenUserId != null && tokenUserId.equals(userId);
    }

    /** 从 query string 里取 deadline（epoch 毫秒），取不到返回 null */
    private Long parseDeadline(Session session) {
        String query = session.getQueryString();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith("deadline=")) {
                try {
                    return Long.parseLong(pair.substring("deadline=".length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void stopCountdownTask(String sessionKey) {
        ScheduledFuture<?> future = TASK_MAP.remove(sessionKey);
        if (future != null) {
            // cancel(false)：不打断正在执行的那一次推送
            future.cancel(false);
        }
    }

    /**
     * 只在「映射里确实是这个 session」时才删。
     *
     * <p>互踢的顺序是<b>先关闭旧连接、再把新 session 放进 map</b>，
     * 而容器对旧连接的 {@code onClose} 回调在另一个线程、通常更晚才触发。
     * 无条件 {@code remove(sessionKey)} 会把<b>新连接</b>从 map 里删掉 ——
     * 学生换设备 / 重连之后（正是这个类宣称要支持的场景）倒计时推送就断了，
     * 到点的 TIMEOUT 自动交卷通知也收不到。</p>
     */
    private static boolean removeSessionIfOwner(String sessionKey, Session session) {
        return session != null && SESSION_MAP.remove(sessionKey, session);
    }

    /**
     * 只取消「属于这个 session」的倒计时任务。
     *
     * <p>句柄同时挂在 session 的 userProperties 上，因此即使 TASK_MAP 里
     * 已被新连接覆盖，旧连接也只取消自己那一个 future，不会误杀新连接的任务。</p>
     */
    private void stopOwnCountdownTask(Session session, String sessionKey) {
        Object own = session == null ? null : session.getUserProperties().get(TASK_PROP);
        if (own instanceof ScheduledFuture<?> future) {
            future.cancel(false);
            TASK_MAP.remove(sessionKey, future);
            return;
        }
        // 兜底：句柄不在 session 上（例如由测试直接驱动）
        stopCountdownTask(sessionKey);
    }

    private void sendMessage(Session session, String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("WebSocket 消息发送失败", e);
        }
    }

    private void closeSession(Session session, String reason) {
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
            }
        } catch (IOException e) {
            log.error("关闭 WebSocket 失败", e);
        }
    }

    /**
     * 从 query string 里取 token。
     *
     * <p>用 UriComponentsBuilder 正经解析而不是 {@code query.startsWith("token=")}：
     * 连接串现在可能带多个参数（{@code ?token=..&deadline=..}），
     * 参数顺序一旦变化，前缀匹配就会静默失效，表现为「怎么都连不上」。</p>
     */
    private String getTokenFromSession(Session session) {
        String query = session.getQueryString();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("token=")) {
                    String raw = pair.substring("token=".length());
                    try {
                        return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return raw;
                    }
                }
            }
        }
        // 兜底：从请求参数表里取
        Map<String, List<String>> params = session.getRequestParameterMap();
        if (params != null && params.containsKey("token")) {
            List<String> tokens = params.get("token");
            if (tokens != null && !tokens.isEmpty()) {
                return tokens.get(0);
            }
        }
        return null;
    }
}