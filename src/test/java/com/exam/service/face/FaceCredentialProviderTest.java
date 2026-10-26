package com.exam.service.face;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 实例 RAM 角色临时凭证的测试：用一个本地假 IMDS 服务代替阿里云元数据服务。
 *
 * <p>为什么值得单独测：**这是整条链路上唯一「本地开发永远不会走到」的分支**。
 * 生产上它一旦坏了，表现是「人脸核验服务不可用」，
 * 而那时你会在凭证、权限、网络三个方向来回猜。</p>
 */
@DisplayName("人脸凭证：实例 RAM 角色（IMDS）")
class FaceCredentialProviderTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger roleHits = new AtomicInteger();
    private static final AtomicInteger credHits = new AtomicInteger();
    private static volatile String credBody;

    @BeforeAll
    static void startFakeImds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            int status = 200;
            if (path.equals("/") || path.isEmpty()) {
                roleHits.incrementAndGet();
                body = "exam-ecs-face";
            } else if (path.endsWith("/exam-ecs-face")) {
                credHits.incrementAndGet();
                body = credBody;
            } else {
                status = 404;
                body = "not found";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopFakeImds() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String imdsUrl() {
        return "http://127.0.0.1:" + port;
    }

    @Test
    @DisplayName("从 IMDS 取到临时凭证，并在有效期内复用（不重复请求）")
    void fetchAndCache() {
        credBody = "{\"AccessKeyId\":\"STS.ak\",\"AccessKeySecret\":\"sk\",\"SecurityToken\":\"tok\","
                + "\"Expiration\":\"" + java.time.Instant.now().plusSeconds(3600) + "\"}";
        roleHits.set(0);
        credHits.set(0);

        FaceCredentialProvider provider = new FaceCredentialProvider(imdsUrl(), "", "", "", 5);
        FaceCredentialProvider.Credential c1 = provider.get();
        assertThat(c1.getAccessKeyId()).isEqualTo("STS.ak");
        assertThat(c1.getSecurityToken()).isEqualTo("tok");
        // 角色名自动发现一次、凭证取一次
        assertThat(roleHits.get()).isEqualTo(1);
        assertThat(credHits.get()).isEqualTo(1);

        FaceCredentialProvider.Credential c2 = provider.get();
        assertThat(c2).isSameAs(c1);
        assertThat(credHits.get()).isEqualTo(1);   // 命中缓存，没有第二次请求
    }

    @Test
    @DisplayName("配置了角色名时不再请求角色名接口")
    void configuredRoleSkipsDiscovery() {
        credBody = "{\"AccessKeyId\":\"STS.ak\",\"AccessKeySecret\":\"sk\",\"SecurityToken\":\"tok\","
                + "\"Expiration\":\"" + java.time.Instant.now().plusSeconds(3600) + "\"}";
        roleHits.set(0);
        credHits.set(0);
        FaceCredentialProvider provider = new FaceCredentialProvider(imdsUrl(), "exam-ecs-face", "", "", 5);
        provider.get();
        assertThat(roleHits.get()).isZero();
        assertThat(credHits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("凭证已过期（30 秒后到期）→ 重新取")
    void expiredCredentialIsRefreshed() {
        credBody = "{\"AccessKeyId\":\"STS.old\",\"AccessKeySecret\":\"sk\",\"SecurityToken\":\"tok\","
                + "\"Expiration\":\"" + java.time.Instant.now().plusSeconds(30) + "\"}";
        credHits.set(0);
        FaceCredentialProvider provider = new FaceCredentialProvider(imdsUrl(), "exam-ecs-face", "", "", 5);
        provider.get();
        provider.get();
        // 到期前 5 分钟就视为不新鲜，所以每次都会重取
        assertThat(credHits.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("实例没有绑定角色 → 抛错并给出可操作的提示")
    void noRoleGivesActionableError() {
        FaceCredentialProvider provider = new FaceCredentialProvider("http://127.0.0.1:1", "", "", "", 3);
        assertThatThrownBy(provider::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实例元数据");
    }

    @Test
    @DisplayName("配了静态 AK 就完全不碰 IMDS（本机调试路径）")
    void staticKeySkipsImds() {
        roleHits.set(0);
        credHits.set(0);
        FaceCredentialProvider provider = new FaceCredentialProvider(imdsUrl(), "", "dev-ak", "dev-sk", 5);
        assertThat(provider.usingStaticKey()).isTrue();
        FaceCredentialProvider.Credential c = provider.get();
        assertThat(c.getAccessKeyId()).isEqualTo("dev-ak");
        assertThat(c.getSecurityToken()).isNull();
        assertThat(roleHits.get()).isZero();
        assertThat(credHits.get()).isZero();
    }
}