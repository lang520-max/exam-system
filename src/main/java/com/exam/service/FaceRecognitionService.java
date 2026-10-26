package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.common.exception.BusinessException;
import com.exam.dto.request.FaceEnrollForm;
import com.exam.dto.request.FaceVerifyForm;
import com.exam.dto.response.FaceStatusVO;
import com.exam.dto.response.FaceVerifyResultVO;
import com.exam.entity.ExamRecord;
import com.exam.entity.FaceVerifyLog;
import com.exam.entity.UserFace;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.FaceVerifyLogMapper;
import com.exam.mapper.UserFaceMapper;
import com.exam.service.face.AliyunFaceClient;
import com.exam.service.face.FaceCredentialProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 人脸识别：底片录入 / 开考前核验 / 教师放行 / 状态查询。
 *
 * <p>三条设计原则（都对应真实会出问题的地方）：</p>
 * <ol>
 *   <li><b>核验结论落库、不落内存</b>：开考门禁查 {@code face_verify_log} 里近期的
 *       PASS/OVERRIDE_PASS。服务重启、以后多实例部署都不会失效，还天然留痕。</li>
 *   <li><b>服务不可用 ≠ 通过，也 ≠ 不通过</b>：厂商超时/欠费/限流时记 UNAVAILABLE，
 *       开考被拦下、由教师人工放行。把它当「不通过」会冤枉学生，当「通过」等于门禁失效。</li>
 *   <li><b>重试上限只算真正的失败</b>：FAIL / LIVENESS_FAIL 计数，UNAVAILABLE 不计 ——
 *       运维问题不该把学生锁死。</li>
 * </ol>
 */
@Slf4j
@Service
public class FaceRecognitionService {

    public static final String SCENE_ENROLL = "ENROLL";
    public static final String SCENE_EXAM_START = "EXAM_START";
    public static final String SCENE_OVERRIDE = "OVERRIDE";

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_LIVENESS_FAIL = "LIVENESS_FAIL";
    public static final String RESULT_UNAVAILABLE = "UNAVAILABLE";
    public static final String RESULT_OVERRIDE_PASS = "OVERRIDE_PASS";

    private final UserFaceMapper faceMapper;
    private final FaceVerifyLogMapper logMapper;
    private final ExamRecordMapper recordMapper;

    private final boolean enabled;
    private final double threshold;
    private final int passWindowSeconds;
    private final int maxRetries;
    private final int maxImageChars;
    private final String endpoint;
    private final String regionId;
    private final String imdsUrl;
    private final String roleName;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final int timeoutSeconds;

    /** 懒建：功能没启用时不碰 IMDS、不建客户端 */
    private volatile AliyunFaceClient client;

    public FaceRecognitionService(UserFaceMapper faceMapper,
                                  FaceVerifyLogMapper logMapper,
                                  ExamRecordMapper recordMapper,
                                  @Value("${app.face.enabled:false}") boolean enabled,
                                  @Value("${app.face.threshold:75}") double threshold,
                                  @Value("${app.face.pass-window-seconds:300}") int passWindowSeconds,
                                  @Value("${app.face.max-retries:3}") int maxRetries,
                                  @Value("${app.face.max-image-chars:400000}") int maxImageChars,
                                  @Value("${app.face.endpoint:https://facebody.cn-shanghai.aliyuncs.com}") String endpoint,
                                  @Value("${app.face.region-id:cn-shanghai}") String regionId,
                                  @Value("${app.face.imds-url:http://100.100.100.200/latest/meta-data/ram/security-credentials}") String imdsUrl,
                                  @Value("${app.face.role-name:}") String roleName,
                                  @Value("${app.face.access-key-id:}") String accessKeyId,
                                  @Value("${app.face.access-key-secret:}") String accessKeySecret,
                                  @Value("${app.face.timeout-seconds:15}") int timeoutSeconds) {
        this.faceMapper = faceMapper;
        this.logMapper = logMapper;
        this.recordMapper = recordMapper;
        this.enabled = enabled;
        this.threshold = threshold;
        this.passWindowSeconds = Math.max(30, passWindowSeconds);
        this.maxRetries = Math.max(1, maxRetries);
        this.maxImageChars = Math.max(1000, maxImageChars);
        this.endpoint = endpoint;
        this.regionId = regionId;
        this.imdsUrl = imdsUrl;
        this.roleName = roleName;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.timeoutSeconds = timeoutSeconds;
        if (enabled) {
            log.info("人脸识别已启用: endpoint={}, 阈值={}, 有效窗口={}s, 重试上限={}",
                    endpoint, threshold, this.passWindowSeconds, this.maxRetries);
        }
    }

    /** 测试用：注入一个指向本地假服务的客户端 */
    void setClientForTest(AliyunFaceClient c) {
        this.client = c;
    }

    /**
     * 启动时把「门禁到底有没有生效」写进日志。
     *
     * <p>关闭状态下开考门禁恒为放行，如果只字不提，运维会以为人脸核验在生效 ——
     * 这种「以为开了其实没开」的静默状态，比配置错误更难发现。</p>
     */
    @jakarta.annotation.PostConstruct
    void logState() {
        if (enabled) {
            log.info("人脸识别门禁：已启用（阈值 {}，重试上限 {}，核验有效期 {} 秒）",
                    threshold, maxRetries, passWindowSeconds);
        } else {
            log.warn("人脸识别门禁：未启用（app.face.enabled=false）—— 注册不采集人脸、开考不做核验。"
                    + "生产环境请设 FACE_ENABLED=true 并给 ECS 绑定含 viapi-facebody:CompareFace 的 RAM 角色。");
        }
    }

    private AliyunFaceClient client() {
        AliyunFaceClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    FaceCredentialProvider creds = new FaceCredentialProvider(
                            imdsUrl, roleName, accessKeyId, accessKeySecret, timeoutSeconds);
                    client = new AliyunFaceClient(endpoint, regionId, creds, timeoutSeconds);
                }
                c = client;
            }
        }
        return c;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public double getThreshold() {
        return threshold;
    }

    public int getPassWindowSeconds() {
        return passWindowSeconds;
    }

    // ---------------------------------------------------------------- 录入

    /**
     * 录入 / 更新人脸底片。
     *
     * <p>注册流程与「登录后自助补录」共用这一个入口：老账号（本项目 4 个演示账号）
     * 就是靠它补上底片的，否则新门禁会让它们直接考不了试。</p>
     */
    @Transactional
    public FaceStatusVO enroll(Long userId, FaceEnrollForm form, String ip) {
        requireEnabled();
        if (Boolean.FALSE.equals(form.getLiveness())) {
            writeLog(userId, null, null, SCENE_ENROLL, RESULT_LIVENESS_FAIL, null, 1, ip,
                    "活体检测未通过（未检测到眨眼）", null);
            throw new BusinessException(400, "活体检测未通过：采集时请正对镜头并眨一下眼");
        }
        String image = normalizeImage(form.getImageBase64());
        String sha = sha256(image);
        UserFace existing = faceMapper.selectByUserId(userId);
        String source = form.getSource() == null || form.getSource().isBlank() ? "SELF" : form.getSource().trim();
        if (existing == null) {
            UserFace face = new UserFace();
            face.setUserId(userId);
            face.setImageBase64(image);
            face.setImageSha256(sha);
            face.setSource(source);
            faceMapper.insert(face);
            log.info("人脸底片录入: userId={}, source={}, 图片字符数={}", userId, source, image.length());
        } else {
            existing.setImageBase64(image);
            existing.setImageSha256(sha);
            existing.setSource(source);
            faceMapper.updateById(existing);
            log.info("人脸底片更新: userId={}, source={}, 图片字符数={}", userId, source, image.length());
        }
        writeLog(userId, null, null, SCENE_ENROLL, RESULT_PASS, null, 1, ip, "底片录入成功", null);
        return status(userId, null);
    }

    // ---------------------------------------------------------------- 核验

    /**
     * 开考前核验。返回结果对象而不是抛异常：前端要根据 result 展示不同界面
     * （重试 / 找老师 / 去录底片），抛异常会把这些信息压成一句 message。
     */
    @Transactional
    public FaceVerifyResultVO verify(Long userId, FaceVerifyForm form, String ip) {
        requireEnabled();
        Long examId = form.getExamId();
        UserFace face = faceMapper.selectByUserId(userId);
        if (face == null) {
            FaceVerifyResultVO vo = base();
            vo.setResult("NEED_ENROLL");
            vo.setPassed(false);
            vo.setMessage("还没有录入人脸底片，请先到「人脸录入」完成采集");
            vo.setRetriesLeft(maxRetries);
            return vo;
        }
        int failed = logMapper.countFailedAttempts(userId, examId);
        if (failed >= maxRetries) {
            writeLog(userId, examId, null, SCENE_EXAM_START, RESULT_FAIL, null, failed, ip,
                    "已达重试上限(" + maxRetries + ")，需教师放行", null);
            FaceVerifyResultVO vo = base();
            vo.setResult("BLOCKED");
            vo.setPassed(false);
            vo.setBlocked(true);
            vo.setRetriesLeft(0);
            vo.setNeedTeacherOverride(true);
            vo.setMessage("人脸核验失败次数已达上限，请联系监考老师人工核验后放行");
            return vo;
        }

        if (Boolean.FALSE.equals(form.getLiveness())) {
            int n = failed + 1;
            writeLog(userId, examId, null, SCENE_EXAM_START, RESULT_LIVENESS_FAIL, null, n, ip,
                    "活体检测未通过（未检测到眨眼）", null);
            return failResult("活体检测未通过：请正对镜头、眨一下眼再试", null, n);
        }

        String image = normalizeImage(form.getImageBase64());
        AliyunFaceClient.CompareResult r = client().compare(face.getImageBase64(), image);
        if (!r.isAvailable()) {
            // 先分「谁的锅」：图片不合格（没人脸/太小/太模糊）是学生自己能纠正的，
            // 该扣一次重试额度并让他重拍；鉴权/限流/网络才是 UNAVAILABLE（拦下 + 教师放行）。
            // 这条分支是真机联调时发现的：先前一律按 UNAVAILABLE，学生会看到
            // "服务不可用，请找监考老师"，而其实他只是没对准镜头。
            if (r.isImageProblem()) {
                int n = failed + 1;
                writeLog(userId, examId, null, SCENE_EXAM_START, RESULT_FAIL, null, n, ip,
                        "图片不合格（" + r.getVendorCode() + "）", null);
                return failResult("照片不合格（没拍到正脸或光线太暗），请正对镜头、保持光线均匀后重拍", null, n);
            }
            writeLog(userId, examId, null, SCENE_EXAM_START, RESULT_UNAVAILABLE, null, failed + 1, ip,
                    truncate(r.getDetail(), 240), null);
            FaceVerifyResultVO vo = base();
            vo.setResult(RESULT_UNAVAILABLE);
            vo.setPassed(false);
            vo.setRetriesLeft(maxRetries - failed);
            vo.setNeedTeacherOverride(true);
            vo.setMessage("人脸核验服务暂时不可用，请联系监考老师人工核验后放行");
            return vo;
        }

        BigDecimal confidence = BigDecimal.valueOf(r.getConfidence()).setScale(2, java.math.RoundingMode.HALF_UP);
        if (r.getConfidence() >= threshold) {
            writeLog(userId, examId, null, SCENE_EXAM_START, RESULT_PASS, confidence, failed + 1, ip,
                    "核验通过（阈值 " + threshold + "）", null);
            FaceVerifyResultVO vo = base();
            vo.setResult(RESULT_PASS);
            vo.setPassed(true);
            vo.setConfidence(confidence);
            vo.setRetriesLeft(maxRetries - failed);
            vo.setMessage("核验通过，请在 " + (passWindowSeconds / 60) + " 分钟内开始考试");
            return vo;
        }

        int n = failed + 1;
        writeLog(userId, examId, null, SCENE_EXAM_START, RESULT_FAIL, confidence, n, ip,
                "相似度 " + confidence + " 低于阈值 " + threshold, null);
        return failResult("人脸与底片不匹配（相似度 " + confidence + "，需要 " + (int) threshold + " 以上）", confidence, n);
    }

    // ---------------------------------------------------------------- 教师放行

    /**
     * 教师/管理员放行：写一条 OVERRIDE_PASS 留痕，开考门禁随后认可它。
     */
    @Transactional
    public void override(Long userId, Long examId, Long operatorId, String reason, String ip) {
        requireEnabled();
        writeLog(userId, examId, null, SCENE_OVERRIDE, RESULT_OVERRIDE_PASS, null, 0, ip,
                reason == null || reason.isBlank() ? "教师人工放行" : truncate(reason, 200), operatorId);
        // 若这名考生这场考试已经有记录（例如失败前已开考过），把记录一并标记
        ExamRecord rec = recordMapper.selectOne(new QueryWrapper<ExamRecord>()
                .eq("exam_id", examId).eq("user_id", userId).last("LIMIT 1"));
        if (rec != null) {
            markRecordVerified(rec.getId(), null);
        }
        log.warn("人脸核验人工放行: userId={}, examId={}, operatorId={}, reason={}", userId, examId, operatorId, reason);
    }

    /**
     * 开考门禁的判据：近期是否有 PASS / OVERRIDE_PASS。
     *
     * <p>功能未启用时恒为 true —— 门禁整体不生效（本机 dev、或临时关掉人脸时，
     * 不能因为没人脸就把考试系统锁死）。</p>
     */
    public boolean hasValidPass(Long userId, Long examId) {
        if (!enabled) {
            return true;
        }
        LocalDateTime since = LocalDateTime.now().minusSeconds(passWindowSeconds);
        return logMapper.countRecentPass(userId, examId, since) > 0;
    }

    /** 开考成功后把核验结论固化到答卷上（成绩单/教师端直接看得到） */
    public void markRecordVerified(Long recordId, BigDecimal confidence) {
        ExamRecord patch = new ExamRecord();
        patch.setId(recordId);
        patch.setFaceVerified(1);
        patch.setFaceConfidence(confidence);
        patch.setFaceVerifiedAt(LocalDateTime.now());
        recordMapper.updateById(patch);
    }

    /** 最近一次通过时的相似度（人工放行为 null），用于写到答卷上 */
    public BigDecimal latestPassConfidence(Long userId, Long examId) {
        FaceVerifyLog latest = logMapper.selectLatest(userId, examId);
        if (latest == null || !RESULT_PASS.equals(latest.getResult())) {
            return null;
        }
        return latest.getConfidence();
    }

    // ---------------------------------------------------------------- 状态

    public FaceStatusVO status(Long userId, Long examId) {
        FaceStatusVO vo = new FaceStatusVO();
        vo.setEnabled(enabled);
        vo.setThreshold(threshold);
        vo.setMaxRetries(maxRetries);
        vo.setPassWindowSeconds(passWindowSeconds);
        UserFace face = userId == null ? null : faceMapper.selectByUserId(userId);
        vo.setEnrolled(face != null);
        if (face != null) {
            vo.setEnrolledAt(face.getUpdatedAt() != null ? face.getUpdatedAt() : face.getCreatedAt());
        }
        if (!enabled || examId == null || userId == null) {
            return vo;
        }
        int failed = logMapper.countFailedAttempts(userId, examId);
        vo.setFailedAttempts(failed);
        vo.setBlocked(failed >= maxRetries);
        LocalDateTime since = LocalDateTime.now().minusSeconds(passWindowSeconds);
        boolean passed = logMapper.countRecentPass(userId, examId, since) > 0;
        vo.setPassed(passed);
        FaceVerifyLog latest = logMapper.selectLatest(userId, examId);
        if (latest != null) {
            vo.setLastResult(latest.getResult());
            vo.setLastDetail(latest.getDetail());
            if (RESULT_PASS.equals(latest.getResult()) || RESULT_OVERRIDE_PASS.equals(latest.getResult())) {
                vo.setConfidence(latest.getConfidence());
                vo.setVerifiedAt(latest.getCreatedAt());
            }
        }
        return vo;
    }

    // ---------------------------------------------------------------- 内部

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(400, "人脸识别未启用（app.face.enabled=false）");
        }
    }

    private FaceVerifyResultVO base() {
        FaceVerifyResultVO vo = new FaceVerifyResultVO();
        vo.setThreshold(threshold);
        return vo;
    }

    private FaceVerifyResultVO failResult(String message, BigDecimal confidence, int attemptNo) {
        FaceVerifyResultVO vo = base();
        vo.setResult(RESULT_FAIL);
        vo.setPassed(false);
        vo.setConfidence(confidence);
        int left = Math.max(0, maxRetries - attemptNo);
        vo.setRetriesLeft(left);
        vo.setBlocked(left == 0);
        vo.setNeedTeacherOverride(left == 0);
        vo.setMessage(left == 0 ? message + "；失败次数已达上限，请联系监考老师放行" : message);
        return vo;
    }

    private void writeLog(Long userId, Long examId, Long recordId, String scene, String result,
                          BigDecimal confidence, Integer retryNo, String ip, String detail, Long operatorId) {
        FaceVerifyLog logRow = new FaceVerifyLog();
        logRow.setUserId(userId);
        logRow.setExamId(examId);
        logRow.setRecordId(recordId);
        logRow.setScene(scene);
        logRow.setResult(result);
        logRow.setConfidence(confidence);
        // 注意：别写成 `cond ? 0 : (cond2 ? 1 : null)` —— 三元表达式会把整体推成 int，
        // null 会被拆箱成 NPE（这里真踩过一次）。显式用 Integer。
        Integer liveness = null;
        if (RESULT_LIVENESS_FAIL.equals(result)) {
            liveness = 0;
        } else if (RESULT_PASS.equals(result)) {
            liveness = 1;
        }
        logRow.setLiveness(liveness);
        logRow.setRetryNo(retryNo);
        logRow.setIpAddress(ip);
        logRow.setDetail(detail);
        logRow.setOperatorId(operatorId);
        logMapper.insert(logRow);
    }

    /** 图片规范化：去前缀/去空白 + 大小上限（防止有人往库里塞几 MB） */
    String normalizeImage(String raw) {
        String image = AliyunFaceClient.stripDataUrlPrefix(raw);
        if (image.isEmpty()) {
            throw new BusinessException(400, "照片为空，请重新采集");
        }
        if (image.length() > maxImageChars) {
            throw new BusinessException(400, "照片过大（" + image.length() + " 字符，上限 " + maxImageChars + "），请重新采集");
        }
        return image;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}