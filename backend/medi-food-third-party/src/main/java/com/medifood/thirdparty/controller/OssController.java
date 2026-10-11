package com.medifood.thirdparty.controller;

import com.aliyun.oss.common.utils.BinaryUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifood.thirdparty.util.OssUtil;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * OSS 文件上传控制器
 * <p>
 * 提供阿里云 OSS 直传签名和文件上传接口。
 * 支持服务端签名后直传（PostObject）和普通上传两种方式。
 * </p>
 *
 * @author MediFood Team
 */
@RestController
public class OssController {

    @Autowired
    private OssUtil ossUtil;

    @Value("${oss.bucket}")
    private String bucket;

    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.access-key-id}")
    private String accessKeyId;

    @Value("${oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${oss.upload.dir:upload}")
    private String uploadDir;

    @Value("${oss.upload.expire-time:3600}")
    private Long expireTime;

    private String getHost() {
        return "https://" + bucket + "." + endpoint;
    }

    /**
     * 生成 OSS 上传签名（用于前端直传）
     * <p>
     * 使用 OSS4-HMAC-SHA256 签名算法，生成前端直传所需的 policy 和 signature。
     * </p>
     *
     * @return 包含 policy、signature、x_oss_credential 等信息的 Map
     */
    @GetMapping("/get_post_signature_for_oss_upload")
    public ResponseEntity<Map<String, String>> getPostSignatureForOssUpload() throws Exception {
        // 获取 x-oss-credential 里的 date，当前日期，格式为 yyyyMMdd
        ZonedDateTime today = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String date = today.format(formatter);

        // 获取 x-oss-date
        ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC);
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String x_oss_date = now.format(formatter2);

        // 步骤1：创建 policy
        String region = endpoint.split("\\.")[0].replace("oss-", "");
        String x_oss_credential = accessKeyId + "/" + date + "/" + region + "/oss/aliyun_v4_request";

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> policy = new HashMap<>();
        policy.put("expiration", generateExpiration(expireTime));

        List<Object> conditions = new ArrayList<>();

        Map<String, String> bucketCondition = new HashMap<>();
        bucketCondition.put("bucket", bucket);
        conditions.add(bucketCondition);

        Map<String, String> signatureVersionCondition = new HashMap<>();
        signatureVersionCondition.put("x-oss-signature-version", "OSS4-HMAC-SHA256");
        conditions.add(signatureVersionCondition);

        Map<String, String> credentialCondition = new HashMap<>();
        credentialCondition.put("x-oss-credential", x_oss_credential);
        conditions.add(credentialCondition);

        Map<String, String> dateCondition = new HashMap<>();
        dateCondition.put("x-oss-date", x_oss_date);
        conditions.add(dateCondition);

        conditions.add(Arrays.asList("content-length-range", 1, 10240000));
        conditions.add(Arrays.asList("eq", "$success_action_status", "200"));
        conditions.add(Arrays.asList("starts-with", "$key", uploadDir));

        policy.put("conditions", conditions);
        String jsonPolicy = mapper.writeValueAsString(policy);

        // 步骤2：构造待签名字符串（StringToSign）
        String stringToSign = new String(Base64.encodeBase64(jsonPolicy.getBytes()));

        // 步骤3：计算 SigningKey
        byte[] dateKey = hmacsha256(("aliyun_v4" + accessKeySecret).getBytes(), date);
        byte[] dateRegionKey = hmacsha256(dateKey, region);
        byte[] dateRegionServiceKey = hmacsha256(dateRegionKey, "oss");
        byte[] signingKey = hmacsha256(dateRegionServiceKey, "aliyun_v4_request");

        // 步骤4：计算 Signature
        byte[] result = hmacsha256(signingKey, stringToSign);
        String signature = BinaryUtil.toHex(result);

        Map<String, String> response = new HashMap<>();
        response.put("version", "OSS4-HMAC-SHA256");
        response.put("policy", stringToSign);
        response.put("x_oss_credential", x_oss_credential);
        response.put("x_oss_date", x_oss_date);
        response.put("signature", signature);
        response.put("dir", uploadDir);
        response.put("host", getHost());

        return ResponseEntity.ok(response);
    }

    /**
     * 普通文件上传接口
     * <p>安全限制：仅允许图片与 PDF，单文件不超过 10MB，
     * 防止将云存储当作任意文件托管（钓鱼页/恶意软件分发）。</p>
     *
     * @param file 上传的文件
     * @param dir  存储目录
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dir", defaultValue = "upload") String dir) throws IOException {
        // 校验文件非空与大小上限（10MB，与直传 policy 的 content-length-range 保持一致）
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("文件不能为空"));
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(errorBody("文件大小不能超过 10MB"));
        }
        // 校验扩展名白名单：仅允许常见图片格式与 PDF
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().body(errorBody("不支持的文件类型，仅允许图片（jpg/jpeg/png/gif/webp/bmp）与 PDF"));
        }

        String url = ossUtil.upload(file, dir);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        return ResponseEntity.ok(result);
    }

    /** 允许上传的文件扩展名白名单（覆盖头像/资质证书/帖子配图等业务场景） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".pdf");

    /** 单文件大小上限：10MB */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    /** 构造错误响应体（与成功响应同为简单 JSON，便于前端统一解析） */
    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return body;
    }

    /**
     * 生成过期时间（ISO8601格式）
     */
    public static String generateExpiration(long seconds) {
        long now = Instant.now().getEpochSecond();
        long expirationTime = now + seconds;
        Instant instant = Instant.ofEpochSecond(expirationTime);
        ZoneId zone = ZoneOffset.UTC;
        ZonedDateTime zonedDateTime = instant.atZone(zone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return zonedDateTime.format(formatter);
    }

    /**
     * HMAC-SHA256 签名
     */
    public static byte[] hmacsha256(byte[] key, String data) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(data.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }
}