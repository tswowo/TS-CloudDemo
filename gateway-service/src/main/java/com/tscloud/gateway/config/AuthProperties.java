package com.tscloud.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "ts.auth")
public class AuthProperties {
    private List<String> includePaths;
    private List<String> excludePaths;
    /** 管理端路径：需要商户角色（role=2）才能访问 */
    private List<String> adminPaths;
}
