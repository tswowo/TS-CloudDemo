package com.hmall.gateway.filters;

import com.hmall.common.utils.UserContext;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 商户角色值，与 user-service 签发逻辑保持一致 */
    private static final Integer ROLE_ADMIN = 2;

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. 管理端请求：校验 token + 商户角色（优先级高于白名单）
        if (isAdminRequest(request)) {
            return handleAdminRequest(exchange, chain);
        }
        // 2. 白名单：放行
        if (isExcludePath(path)) {
            return chain.filter(exchange);
        }
        // 3. 其余请求：要求登录
        return handleLoginRequest(exchange, chain);
    }

    /** 管理端请求：必须携带有效 token 且 role=2，否则 401/403 */
    private Mono<Void> handleAdminRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = getToken(request);
        Long userId;
        Integer role;
        try {
            userId = jwtTool.parseToken(token);
            role = jwtTool.parseRole(token);
        } catch (Exception e) {
            return unauthorized(exchange);
        }
        if (!ROLE_ADMIN.equals(role)) {
            return forbidden(exchange);
        }
        return passUserId(exchange, chain, userId);
    }

    /** 普通请求：要求登录，校验 token 后放行 */
    private Mono<Void> handleLoginRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String token = getToken(request);
        Long userId;
        try {
            userId = jwtTool.parseToken(token);
        } catch (Exception e) {
            return unauthorized(exchange);
        }
        return passUserId(exchange, chain, userId);
    }

    private Mono<Void> passUserId(ServerWebExchange exchange, GatewayFilterChain chain, Long userId) {
        // 传递用户信息给下游
        exchange.mutate()
                .request(
                        builder -> builder.header(UserContext.USER_HEADER, userId.toString())
                                .build()
                );
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    private String getToken(ServerHttpRequest request) {
        List<String> tokens = request.getHeaders().get("Authorization");
        if (tokens != null && !tokens.isEmpty()) {
            return tokens.get(0);
        }
        return null;
    }

    /**
     * 管理端请求判定：
     * 1. 命中 adminPaths 配置的路径（任何方法），如 /orders/page、/upload/**
     * 2. 商品模块的写操作（POST/PUT/DELETE /items/**）——GET 浏览保持公开
     */
    private boolean isAdminRequest(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();
        List<String> adminPaths = authProperties.getAdminPaths();
        if (adminPaths != null) {
            for (String adminPath : adminPaths) {
                if (matcher.match(adminPath, path)) {
                    return true;
                }
            }
        }
        return !HttpMethod.GET.equals(method) && matcher.match("/items/**", path);
    }

    private boolean isExcludePath(String value) {
        List<String> excludePaths = authProperties.getExcludePaths();
        if (excludePaths == null) {
            return false;
        }
        for (String excludePath : excludePaths) {
            if (matcher.match(excludePath, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
