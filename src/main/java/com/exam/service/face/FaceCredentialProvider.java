package com.exam.service.face;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 人脸 API 的凭证提供者：默认走**ECS 实例 RAM 角色**，服务器上不放长期密钥。
 *
 * <p>为什么这样做：AK/SK 一旦落盘（哪怕文件权限 600），泄漏就等于别人拿你的账号
 * 花钱调 API。阿里云的实例 RAM 角色让应用从实例元数据服务（IMDS，链路本地地址
 * 100.100.100.200）拿**临时凭证**，到期自动轮换；角色权限可以只给
 * {@code viapi-facebody:CompareFace} 一个 Action。</p>
 *
 * <p>两种取值顺序：
 * <ol>
 *   <li>配置里显式给了 {@code access-key-id/secret}（本机调试、或临时排障）→ 直接用；</li>
 *   <li>否则向 IMDS 要：先取角色名（配置留空时自动取第一个），再取该角色的临时凭证，
 *       缓存到过期前 5 分钟。</li>
 * </ol>
 * IMDS 地址可配置（{@code app.face.imds-url}），测试里指向本地假服务即可。</p>
 */
@Slf4j
public class FaceCredentialProvider {

    private static final long REFRESH_BEFORE_EXPIRE_SECONDS = 300;

    private final String imdsUrl;
    private final String configuredRoleName;
    private final String staticAccessKeyId;
    private final String staticAccessKeySecret;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Credential cached;

    public FaceCredentialProvider(String imdsUrl,
                                  String configuredRoleName,
                                  String staticAccessKeyId,
                                  String staticAccessKeySecret,
                                  int timeoutSeconds) {
        this.imdsUrl = trimTrailingSlash(imdsUrl);
        this.configuredRoleName = configuredRoleName == null ? "" : configuredRoleName.trim();
        this.staticAccessKeyId = staticAccessKeyId == null ? "" : staticAccessKeyId.trim();
        this.staticAccessKeySecret = staticAccessKeySecret == null ? "" : staticAccessKeySecret.trim();
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** 是否配了静态密钥（用于日志里区分「RAM 角色」还是「显式 AK」） */
    public boolean usingStaticKey() {
        return !staticAccessKeyId.isEmpty() && !staticAccessKeySecret.isEmpty();
    }

    /**
     * 取一份可用凭证。拿不到就抛 {@link IllegalStateException} ——
     * 由调用方转成「人脸服务不可用」，绝不静默放行。
     */
    public Credential get() {
        if (usingStaticKey()) {
            return new Credential(staticAccessKeyId, staticAccessKeySecret, null, null);
        }
        Credential c = cached;
        if (c != null && c.isFresh()) {
            return c;
        }
        synchronized (this) {
            if (cached != null && cached.isFresh()) {
                return cached;
            }
            cached = fetchFromImds();
            return cached;
        }
    }

    private Credential fetchFromImds() {
        String role = configuredRoleName.isEmpty() ? fetchRoleName() : configuredRoleName;
        if (role.isEmpty()) {
            throw new IllegalStateException("实例上没有可用的 RAM 角色（IMDS 返回空），"
                    + "请给 ECS 绑定角色，或在 app.face.access-key-id/secret 里显式配置");
        }
        String body = httpGet(imdsUrl + "/" + role);
        try {
            JsonNode n = mapper.readTree(body);
            String ak = text(n, "AccessKeyId");
            String sk = text(n, "AccessKeySecret");
            String token = text(n, "SecurityToken");
            if (ak.isEmpty() || sk.isEmpty()) {
                throw new IllegalStateException("IMDS 返回的临时凭证不完整: " + body);
            }
            LocalDateTime expire = parseExpiration(text(n, "Expiration"));
            log.info("已从实例 RAM 角色取得临时凭证: role={}, 过期时间={}", role, expire);
            return new Credential(ak, sk, token, expire);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析 IMDS 凭证失败: " + e.getMessage(), e);
        }
    }

    private String fetchRoleName() {
        String body = httpGet(imdsUrl + "/");
        // IMDS 返回的是纯文本角色名（一行）；没有角色时返回 404 → httpGet 抛异常，这里兜住
        return body == null ? "" : body.trim();
    }

    private String httpGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("X-aliyun-ecs-metadata-token-ttl-seconds", "21600")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("IMDS 返回 HTTP " + resp.statusCode() + "（" + url + "）"
                        + "—— 实例可能没有绑定 RAM 角色");
            }
            return resp.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("访问实例元数据失败（" + url + "）: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static LocalDateTime parseExpiration(String iso) {
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimTrailingSlash(String s) {
        String v = s == null ? "" : s.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    /** 一份凭证（SecurityToken 仅在 STS 临时凭证时有值） */
    public static class Credential {
        private final String accessKeyId;
        private final String accessKeySecret;
        private final String securityToken;
        private final LocalDateTime expireAt;

        Credential(String accessKeyId, String accessKeySecret, String securityToken, LocalDateTime expireAt) {
            this.accessKeyId = accessKeyId;
            this.accessKeySecret = accessKeySecret;
            this.securityToken = securityToken;
            this.expireAt = expireAt;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public String getSecurityToken() {
            return securityToken;
        }

        boolean isFresh() {
            return expireAt == null || expireAt.isAfter(LocalDateTime.now().plusSeconds(REFRESH_BEFORE_EXPIRE_SECONDS));
        }
    }
}