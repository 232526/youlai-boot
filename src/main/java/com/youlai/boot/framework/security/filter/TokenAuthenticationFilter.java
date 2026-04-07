package com.youlai.boot.framework.security.filter;

import cn.hutool.core.util.StrUtil;
import com.youlai.boot.common.constant.SecurityConstants;
import com.youlai.boot.common.result.ResultCode;
import com.youlai.boot.common.result.ResponseWriter;
import com.youlai.boot.framework.security.token.TokenManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Token 认证校验过滤器
 *
 * @author wangtao
 * @since 2025/3/6 16:50
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Token 管理器
     */
    private final TokenManager tokenManager;

    /**
     * 白名单路径（这些路径即使 Token 无效也不拦截）
     */
    private final String[] ignoreUrls;

    /**
     * 路径匹配器
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TokenAuthenticationFilter(TokenManager tokenManager, String[] ignoreUrls) {
        this.tokenManager = tokenManager;
        this.ignoreUrls = ignoreUrls;
    }

    /**
     * 校验 Token ，包括验签和是否过期
     * 如果 Token 有效，将 Token 解析为 Authentication 对象，并设置到 Spring Security 上下文中
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String rawToken = resolveToken(request);

        try {
            if (StrUtil.isNotBlank(rawToken)) {
                // 执行令牌有效性检查（包含密码学验签和过期时间验证）
                boolean isValidToken = tokenManager.validateToken(rawToken);
                if (!isValidToken) {
                    // 白名单路径即使 Token 无效也不拦截，允许继续访问
                    if (isIgnoreUrl(request)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    ResponseWriter.writeError(response, ResultCode.ACCESS_TOKEN_INVALID);
                    return;
                }

                // 将令牌解析为 Spring Security 上下文认证对象
                Authentication authentication = tokenManager.parseToken(rawToken);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            // 安全上下文清除保障（防止上下文残留）
            SecurityContextHolder.clearContext();
            // 白名单路径即使 Token 解析异常也不拦截，允许继续访问
            if (isIgnoreUrl(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            ResponseWriter.writeError(response, ResultCode.ACCESS_TOKEN_INVALID);
            return;
        }

        // 继续后续过滤器链执行
        filterChain.doFilter(request, response);
    }

    /**
     * 检查当前请求是否为白名单路径
     */
    private boolean isIgnoreUrl(HttpServletRequest request) {
        if (ignoreUrls == null || ignoreUrls.length == 0) {
            return false;
        }
        String requestUri = request.getRequestURI();
        return Arrays.stream(ignoreUrls)
                .anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }

    /**
     * 从请求中解析 Token（仅支持 Authorization Header）
     */
    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotBlank(authorizationHeader)
                && authorizationHeader.startsWith(SecurityConstants.BEARER_TOKEN_PREFIX)) {
            return authorizationHeader.substring(SecurityConstants.BEARER_TOKEN_PREFIX.length());
        }
        return null;
    }
}
