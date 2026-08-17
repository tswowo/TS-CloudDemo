package com.tscloud.item.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 连接配置。密钥不落配置文件，通过环境变量/配置中心注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ts.oss")
public class OssProperties {

    /** 地域节点，如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    /** 桶名 */
    private String bucketName;
}
