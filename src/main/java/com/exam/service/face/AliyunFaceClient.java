package com.exam.service.face;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 阿里云「视觉智能开放平台 - 人脸比对 1:1（CompareFace）」客户端。
 *
 * <p>零新依赖：HTTP 用 JDK 自带的 {@link HttpClient}，签名用 {@code javax.crypto.Mac}
 * 手写 RPC 风格签名（与项目里 AI 评分那边「不引任何新依赖」的做法一致）。
 * 不引官方 SDK 的代价是要自己写签名，但换来的是：可测试（endpoint 可指向本地假服务）、
 * 依赖面不变、出错时错误码原样暴露。</p>
 *
 * <p><b>失败语义很重要</b>：本类不抛业务异常，一律返回
 * {@link CompareResult}，其中 {@code available=false} 表示「没拿到结论」
 * （网络/鉴权/限流/参数错），调用方必须当成**不可用**处理（拦下 + 教师放行），
 * 绝不能当成「不通过」或「通过」。</p>
 */
@Slf4j
public class AliyunFaceClient {

    private static final String ACTION = "CompareFace";
    private static final String VERSION = "2019-12-30";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final String endpoint;
    private final String regionId;
    private final FaceCredentialProvider credentials;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public AliyunFaceClient(String endpoint,
                            String regionId,
                            FaceCredentialProvider credentials,
                            int timeoutSeconds) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.regionId = regionId == null ? "" : regionId.trim();
        this.credentials = credentials;
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 比对两张人脸，返回相似度（0-100，越高越像）。
     *
     * @param imageBase64A 注册底片（base64，可带 data:image/jpeg;base64, 前缀）
     * @param imageBase64B 现场抓拍
     */
    public CompareResult compare(String imageBase64A, String imageBase64B) {
        String a = stripDataUrlPrefix(imageBase64A);
        String b = stripDataUrlPrefix(imageBase64B);
        if (a.isEmpty() || b.isEmpty()) {
            return CompareResult.unavailable("图片为空");
        }
        FaceCredentialProvider.Credential cred;
        try {
            cred = credentials.get();
        } catch (Exception e) {
            log.warn("取人脸 API 凭证失败: {}", e.getMessage());
            return CompareResult.unavailable("取凭证失败: " + e.getMessage());
        }

        Map<String, String> params = new TreeMap<>();
        params.put("Action", ACTION);
        params.put("Version", VERSION);
        params.put("Format", "JSON");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Timestamp", TIMESTAMP_FMT.format(Instant.now()));
        params.put("AccessKeyId", cred.getAccessKeyId());
        if (!regionId.isEmpty()) {
            params.put("RegionId", regionId);
        }
        if (cred.getSecurityToken() != null && !cred.getSecurityToken().isEmpty()) {
            params.put("SecurityToken", cred.getSecurityToken());
        }
        params.put("ImageDataA", a);
        params.put("ImageDataB", b);

        String signature;
        try {
            signature = sign(params, cred.getAccessKeySecret());
        } catch (Exception e) {
            return CompareResult.unavailable("签名失败: " + e.getMessage());
        }
        params.put("Signature", signature);

        String form = toFormBody(params);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parse(resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.warn("调用人脸比对失败: {}", e.getMessage());
            return CompareResult.unavailable("请求失败: " + e.getMessage());
        }
    }

    /**
     * 解析厂商响应；任何拿不到 Confidence 的情况都算「不可用」。
     *
     * <p><b>注意：业务错误不一定带 200。</b>实测云端的「画面里没有人脸」是
     * <b>HTTP 404</b> + {@code {"Code":"InvalidImage.FaceNotDetected",...}} ——
     * 所以非 200 也必须先尝试解析 body 里的 {@code Code}，否则
     * {@link CompareResult#isImageProblem()} 永远不成立，学生会看到
     * "服务不可用，请找监考老师"，而他只是没对准镜头（真机联调踩到过）。</p>
     */
    CompareResult parse(int status, String body) {
        String shortBody = body == null ? "" : body.substring(0, Math.min(300, body.length()));
        JsonNode root = null;
        try {
            root = mapper.readTree(body);
        } catch (Exception ignored) {
            // body 不是 JSON（网关错误页等）：保持 root=null，走下面的「不可用」分支
        }
        JsonNode code = root == null ? null : root.get("Code");
        boolean hasCode = code != null && !code.isNull() && !code.asText("").isEmpty();
        if (hasCode) {
            String codeText = code.asText("");
            String msg = root.path("Message").asText("");
            String prefix = status == 200 ? "" : "HTTP " + status + " ";
            return CompareResult.error(codeText, prefix + "厂商错误 " + codeText + ": " + msg);
        }
        if (status != 200) {
            return CompareResult.unavailable("HTTP " + status + " " + shortBody);
        }
        if (root == null) {
            return CompareResult.unavailable("响应不是 JSON: " + shortBody);
        }
        JsonNode data = root.get("Data");
        if (data == null || data.isNull() || data.get("Confidence") == null) {
            return CompareResult.unavailable("响应缺少 Confidence: " + shortBody);
        }
        double confidence = data.get("Confidence").asDouble();
        Double qualityA = data.hasNonNull("QualityScoreA") ? data.get("QualityScoreA").asDouble() : null;
        Double qualityB = data.hasNonNull("QualityScoreB") ? data.get("QualityScoreB").asDouble() : null;
        return CompareResult.ok(confidence, qualityA, qualityB);
    }

    // ---------------------------------------------------------------- 签名

    /**
     * 阿里云 RPC 风格签名：
     * <pre>
     *   canonicalizedQueryString = 参数按 key 排序后 "k=percentEncode(v)" 用 & 连接
     *   stringToSign = "POST&" + percentEncode("/") + "&" + percentEncode(canonicalizedQueryString)
     *   Signature    = Base64(HMAC-SHA1(accessKeySecret + "&", stringToSign))
     * </pre>
     */
    static String sign(Map<String, String> params, String accessKeySecret) throws Exception {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            if (canonical.length() > 0) {
                canonical.append('&');
            }
            canonical.append(percentEncode(e.getKey())).append('=').append(percentEncode(e.getValue()));
        }
        String stringToSign = "POST&" + percentEncode("/") + "&" + percentEncode(canonical.toString());
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] raw = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    /** 阿里云要求的百分号编码：空格→%20、*→%2A、~ 保持原样 */
    static String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static String toFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(percentEncode(e.getKey())).append('=').append(percentEncode(e.getValue()));
        }
        return sb.toString();
    }

    /** 去掉 data:image/jpeg;base64, 前缀，并去掉空白字符 */
    public static String stripDataUrlPrefix(String image) {
        if (image == null) {
            return "";
        }
        String v = image.trim();
        int idx = v.indexOf("base64,");
        if (v.startsWith("data:") && idx > 0) {
            v = v.substring(idx + "base64,".length());
        }
        return v.replaceAll("\\s", "");
    }

    /** 比对结果：要么拿到相似度，要么「不可用」 */
    public static class CompareResult {
        private final boolean available;
        private final double confidence;
        private final Double qualityA;
        private final Double qualityB;
        private final String detail;
        /** 厂商错误码（成功时为 null）——用来区分「谁的锅」 */
        private final String vendorCode;

        private CompareResult(boolean available, double confidence, Double qualityA, Double qualityB,
                              String detail, String vendorCode) {
            this.available = available;
            this.confidence = confidence;
            this.qualityA = qualityA;
            this.qualityB = qualityB;
            this.detail = detail;
            this.vendorCode = vendorCode;
        }

        static CompareResult ok(double confidence, Double qualityA, Double qualityB) {
            return new CompareResult(true, confidence, qualityA, qualityB, "OK", null);
        }

        static CompareResult unavailable(String detail) {
            return new CompareResult(false, 0, null, null, detail, null);
        }

        /** 厂商明确返回了错误码 */
        static CompareResult error(String vendorCode, String detail) {
            return new CompareResult(false, 0, null, null, detail, vendorCode);
        }

        public boolean isAvailable() {
            return available;
        }

        /**
         * 是不是「学生自己能纠正」的图片问题。
         *
         * <p>实测踩到：画面里没有人脸时厂商返回 {@code InvalidImage.*}（例如
         * {@code InvalidImage.Resolution}、{@code InvalidImage.FaceNotDetected}）。
         * 这类错误原先被一律当成「服务不可用」→ 学生看到"请找监考老师"，
         * 而正确语义是"照片不合格，重拍一次"：**该扣一次重试额度、学生自己能改**。
         * 只有鉴权/限流/网络这类才该走 UNAVAILABLE + 教师放行。</p>
         */
        public boolean isImageProblem() {
            if (vendorCode == null) {
                return false;
            }
            String c = vendorCode;
            return c.startsWith("InvalidImage")
                    || c.contains("FaceNotDetected")
                    || c.contains("FaceNotFound")
                    || c.contains("NoFace")
                    || c.contains("ImageTooLarge");
        }

        public String getVendorCode() {
            return vendorCode;
        }

        public double getConfidence() {
            return confidence;
        }

        public Double getQualityA() {
            return qualityA;
        }

        public Double getQualityB() {
            return qualityB;
        }

        public String getDetail() {
            return detail;
        }
    }
}