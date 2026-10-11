package com.medifood.thirdparty.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传工具类
 * <p>
 * 提供文件上传、删除、获取访问URL等功能。
 * 注意：third-party 模块不依赖 common 模块，自行实现 OssUtil。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
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

    /**
     * 上传文件到 OSS
     *
     * @param file 上传的文件
     * @param dir  存储目录
     * @return 文件访问URL
     */
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
            log.info("OSS上传成功: objectName={}", objectName);
            return getAccessUrl(objectName);
        } catch (Exception e) {
            log.error("OSS上传失败: dir={}, file={}", dir, originalName, e);
            throw new RuntimeException("文件上传失败");
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 删除 OSS 上的文件
     *
     * @param objectUrl 文件URL
     */
    public void delete(String objectUrl) {
        String objectName = extractObjectName(objectUrl);
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucket, objectName);
            log.info("OSS删除成功: objectName={}", objectName);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 获取文件访问URL
     *
     * @param objectName OSS 对象名
     * @return 文件访问URL
     */
    public String getAccessUrl(String objectName) {
        if (domain != null && !domain.isEmpty()) {
            return domain.replaceAll("/$", "") + "/" + objectName;
        }
        return "https://" + bucket + "." + endpoint + "/" + objectName;
    }

    /**
     * 从URL中提取OSS对象名
     */
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