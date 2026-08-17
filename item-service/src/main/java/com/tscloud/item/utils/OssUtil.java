package com.tscloud.item.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.tscloud.common.exception.BadRequestException;
import com.tscloud.item.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 图片上传工具（服务端中转模式）：
 * 客户端上传文件到应用服务 → 应用服务调用 OSS SDK putObject → 返回可访问 URL。
 * 对比前端直传：密钥不暴露给客户端，但文件流量经过应用服务器；高流量场景可改用 STS 临时凭证直传。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OssUtil {

    private final OssProperties properties;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // UUID 命名防止同名覆盖，按业务目录归档
        String objectName = "item/" + UUID.randomUUID() + ext;

        OSS client = new OSSClientBuilder().build(
                properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
        try (InputStream in = file.getInputStream()) {
            client.putObject(properties.getBucketName(), objectName, in);
        } catch (Exception e) {
            log.error("图片上传 OSS 失败, objectName={}", objectName, e);
            throw new BadRequestException("图片上传失败");
        } finally {
            client.shutdown();
        }
        // 访问 URL：https://{bucket}.{endpoint}/{objectName}
        String endpoint = properties.getEndpoint();
        if (endpoint.startsWith("https://")) {
            endpoint = endpoint.substring("https://".length());
        }
        return "https://" + properties.getBucketName() + "." + endpoint + "/" + objectName;
    }
}
