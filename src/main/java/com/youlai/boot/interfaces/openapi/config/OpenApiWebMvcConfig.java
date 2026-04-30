package com.youlai.boot.interfaces.openapi.config;

import com.youlai.boot.interfaces.openapi.interceptor.OpenApiSignatureInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开放API WebMvc配置
 * <p>
 * 注册签名验证拦截器，拦截 /api/v1/open/** 路径的请求
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiWebMvcConfig implements WebMvcConfigurer {

    private final OpenApiSignatureInterceptor openApiSignatureInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiSignatureInterceptor)
                .addPathPatterns("/api/v1/open/**");
    }
}
