package com.medifood.common.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "oss.endpoint")
public class OssUtil {

    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.access-key-id}")
    private String accessKeyId;

    @Value("${oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${oss.bucket}")
    private String bucket;

    @Value("${oss.domain:}")
    private String domain;

    public String upload(MultipartFile file, String dir) {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String objectName = dir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream is = file.getInputStream()) {
            ossClient.putObject(bucket, objectName, is);
            return getAccessUrl(objectName);
        } catch (Exception e) {
            log.error("OSS上传失败: dir={}, file={}", dir, originalName, e);
            throw new RuntimeException("文件上传失败");
        } finally {
            ossClient.shutdown();
        }
    }

    public void delete(String objectUrl) {
        String objectName = extractObjectName(objectUrl);
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucket, objectName);
        } finally {
            ossClient.shutdown();
        }
    }

    public String getAccessUrl(String objectName) {
        if (domain != null && !domain.isEmpty()) {
            return domain.replaceAll("/$", "") + "/" + objectName;
        }
        return "https://" + bucket + "." + endpoint + "/" + objectName;
    }

    private String extractObjectName(String url) {
        String prefix = "https://" + bucket + "." + endpoint + "/";
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        if (domain != null && !domain.isEmpty() && url.startsWith(domain)) {
            return url.substring(domain.replaceAll("/$", "").length() + 1);
        }
        return url.substring(url.lastIndexOf("/") + 1);
    }
}