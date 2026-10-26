package com.exam.service.face;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阿里云 RPC 签名与响应解析的单元测试（不启 Spring、不联网）。
 *
 * <p>签名这块**必须锁死**：写错了本地一切正常、上线后所有调用都返回
 * SignatureDoesNotMatch，而那时再排错要先怀疑凭证、再怀疑权限，很费时间。
 * 期望值是用 Node 的 crypto 独立算出来的（不是拿本实现自证），见下方注释。</p>
 */
@DisplayName("阿里云人脸 API：签名与解析")
class AliyunFaceClientTest {

    private static final String SECRET = "test-secret-key";

    @Test
    @DisplayName("签名：与独立实现（Node crypto）算出的值一致")
    void signMatchesIndependentImplementation() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("A", "1");
        params.put("B", "2");
        // canonical = A=1&B=2
        // stringToSign = POST&%2F&A%3D1%26B%3D2
        // 期望值由 node -e "crypto.createHmac('sha1','test-secret-key&')..." 独立算出
        assertThat(AliyunFaceClient.sign(params, SECRET)).isEqualTo("6luWW72CS9YDZhqsfNNP2j3zHq0=");
    }

    @Test
    @DisplayName("百分号编码：空格→%20、*→%2A、~ 保持、斜杠与中文按 UTF-8")
    void percentEncodeFollowsAliyunRules() {
        assertThat(AliyunFaceClient.percentEncode("a b")).isEqualTo("a%20b");
        assertThat(AliyunFaceClient.percentEncode("a*b")).isEqualTo("a%2Ab");
        assertThat(AliyunFaceClient.percentEncode("a~b")).isEqualTo("a~b");
        assertThat(AliyunFaceClient.percentEncode("a/b")).isEqualTo("a%2Fb");
        assertThat(AliyunFaceClient.percentEncode("对比")).isEqualTo("%E5%AF%B9%E6%AF%94");
    }

    @Test
    @DisplayName("签名：含需转义字符的参数集也与独立实现一致")
    void signWithEscapes() throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Action", "CompareFace");
        params.put("ImageDataA", "a b*c~d/e");
        params.put("Timestamp", "2026-09-16T12:00:00Z");
        assertThat(AliyunFaceClient.sign(params, SECRET)).isEqualTo("/X032CQONFew/+LIHI6/GHY9o8A=");
    }

    @Test
    @DisplayName("解析：正常响应取到 Confidence 与质量分")
    void parseOk() {
        AliyunFaceClient client = new AliyunFaceClient("http://127.0.0.1:1", "cn-shanghai", null, 5);
        String body = "{\"RequestId\":\"x\",\"Data\":{\"Confidence\":88.5,\"QualityScoreA\":95.1,\"QualityScoreB\":90.2}}";
        AliyunFaceClient.CompareResult r = client.parse(200, body);
        assertThat(r.isAvailable()).isTrue();
        assertThat(r.getConfidence()).isEqualTo(88.5);
        assertThat(r.getQualityB()).isEqualTo(90.2);
    }

    @Test
    @DisplayName("解析：厂商错误码 → 不可用（而不是「不通过」）")
    void parseVendorError() {
        AliyunFaceClient client = new AliyunFaceClient("http://127.0.0.1:1", "cn-shanghai", null, 5);
        AliyunFaceClient.CompareResult r = client.parse(200,
                "{\"Code\":\"InvalidAccessKeyId.NotFound\",\"Message\":\"Specified Access Key is not found.\"}");
        assertThat(r.isAvailable()).isFalse();
        assertThat(r.getDetail()).contains("InvalidAccessKeyId");
    }

    @Test
    @DisplayName("业务错误也可能带非 200：云端实测「没人脸」是 HTTP 404 + InvalidImage.*")
    void vendorErrorWithNonOkStatusStillClassified() {
        AliyunFaceClient client = new AliyunFaceClient("http://127.0.0.1:1", "cn-shanghai", null, 5);
        String body = "{\"RequestId\":\"x\",\"HostId\":\"facebody.cn-shanghai.aliyuncs.com\","
                + "\"Code\":\"InvalidImage.FaceNotDetected\",\"Message\":\"未检测到人脸\"}";
        AliyunFaceClient.CompareResult r = client.parse(404, body);
        assertThat(r.isAvailable()).isFalse();
        assertThat(r.isImageProblem()).isTrue();          // ← 这是关键：404 也要能识别出是图片问题
        assertThat(r.getDetail()).contains("HTTP 404").contains("InvalidImage.FaceNotDetected");
        // 非 200 且不是图片问题 → 仍是不可用
        AliyunFaceClient.CompareResult r2 = client.parse(500, "{\"Code\":\"InternalError\"}");
        assertThat(r2.isImageProblem()).isFalse();
        // 非 200 且不是 JSON → 不可用
        assertThat(client.parse(502, "<html>bad gateway</html>").isAvailable()).isFalse();
    }

    @Test
    @DisplayName("厂商错误码分类：图片问题是学生能纠正的，鉴权/限流不是")
    void imageProblemClassification() {
        AliyunFaceClient client = new AliyunFaceClient("http://127.0.0.1:1", "cn-shanghai", null, 5);
        // 实测抓到的真实错误码（1×1 测试图 + 没有人脸的图）
        assertThat(client.parse(200, "{\"Code\":\"InvalidImage.Resolution\"}").isImageProblem()).isTrue();
        assertThat(client.parse(200, "{\"Code\":\"InvalidImage.FaceNotDetected\"}").isImageProblem()).isTrue();
        assertThat(client.parse(200, "{\"Code\":\"InvalidImage.TooBlur\"}").isImageProblem()).isTrue();
        // 这些不是学生的锅：该走 UNAVAILABLE + 教师放行
        assertThat(client.parse(200, "{\"Code\":\"Throttling\"}").isImageProblem()).isFalse();
        assertThat(client.parse(200, "{\"Code\":\"InvalidAccessKeyId.NotFound\"}").isImageProblem()).isFalse();
        assertThat(client.parse(200, "{\"Code\":\"SignatureDoesNotMatch\"}").isImageProblem()).isFalse();
        // 网络/解析失败没有厂商错误码，也不该被当成学生的错
        assertThat(client.parse(500, "boom").isImageProblem()).isFalse();
        assertThat(client.parse(200, "not json").isImageProblem()).isFalse();
    }

    @Test
    @DisplayName("解析：HTTP 非 200、缺 Confidence、非 JSON 都算不可用")
    void parseBrokenResponses() {
        AliyunFaceClient client = new AliyunFaceClient("http://127.0.0.1:1", "cn-shanghai", null, 5);
        assertThat(client.parse(500, "boom").isAvailable()).isFalse();
        assertThat(client.parse(200, "{\"Data\":{}}").isAvailable()).isFalse();
        assertThat(client.parse(200, "not json").isAvailable()).isFalse();
        assertThat(client.parse(200, null).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("图片前缀与空白会被清理")
    void stripPrefix() {
        assertThat(AliyunFaceClient.stripDataUrlPrefix("data:image/jpeg;base64,AAAA")).isEqualTo("AAAA");
        assertThat(AliyunFaceClient.stripDataUrlPrefix("  AAAA\nBBBB ")).isEqualTo("AAAABBBB");
        assertThat(AliyunFaceClient.stripDataUrlPrefix(null)).isEmpty();
    }
}