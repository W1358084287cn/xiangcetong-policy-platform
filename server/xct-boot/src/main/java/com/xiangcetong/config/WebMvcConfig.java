package com.xiangcetong.config;

import com.xiangcetong.auth.interceptor.AuthInterceptor;
import com.xiangcetong.common.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WebMvc：CORS、鉴权拦截器、静态上传目录映射。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProperties appProperties;

    public WebMvcConfig(AuthInterceptor authInterceptor, AppProperties appProperties) {
        this.authInterceptor = authInterceptor;
        this.appProperties = appProperties;
    }

    /** 允许 H5(5173) 与 Admin(5174) 本地跨域调接口 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册鉴权拦截器。
     * 登录与公开政策、静态资源放行；其余 /api/** 走 Token 校验。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/policy/list",
                        "/api/policy/search"
                );
    }

    /** 把 /uploads/** 映射到本地 uploads 物理目录 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
        String location = uploadPath.toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}