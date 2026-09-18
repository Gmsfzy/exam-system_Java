package com.exam.backend.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class WebConfig {

    /** 前端表单/Excel 常用格式 */
    private static final DateTimeFormatter SPACE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = allowedOrigins.split(",");
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    /**
     * 生产单端口 SPA 托管 (v4.0)：
     * <ul>
     *   <li>{@code /assets/**} 带 hash 的构建产物长缓存（1 年 immutable）；</li>
     *   <li>{@code /**} 兜底：命中静态文件则直出，未命中且非 API/上传/带后缀资源时回退 index.html，
     *       支持 Vue history 模式深链刷新。</li>
     * </ul>
     */
    @Bean
    public WebMvcConfigurer spaConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/assets/**")
                        .addResourceLocations("classpath:/static/assets/")
                        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
                registry.addResourceHandler("/**")
                        .addResourceLocations("classpath:/static/")
                        .resourceChain(true)
                        .addResolver(new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                                Resource requested = super.getResource(resourcePath, location);
                                if (requested != null) {
                                    return requested;
                                }
                                if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws")
                                        || resourcePath.startsWith("uploads/") || resourcePath.contains(".")) {
                                    return null; // 交由 REST/静态资源正常处理或 404
                                }
                                return super.getResource("index.html", location);
                            }
                        });
            }
        };
    }

    /**
     * 兼容两种 LocalDateTime 入参格式：
     * 1) ISO-8601（2026-09-20T09:00:00，Jackson 默认）
     * 2) 空格分隔（2026-09-20 09:00:00，前端表单/Element Plus 常用）
     */
    public static class LenientLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

        public LenientLocalDateTimeDeserializer() {
            super(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            // 含空格且非 ISO（ISO 形如 2026-09-20T09:00:00），按空格格式解析
            if (value.indexOf(' ') > 0 && value.indexOf('T') < 0) {
                try {
                    return LocalDateTime.parse(value, SPACE_FORMATTER);
                } catch (DateTimeParseException ignored) {
                    // 落到父类默认 ISO 解析，由其抛出标准异常
                }
            }
            return super.deserialize(parser, context);
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 输出保持 ISO 格式（与现有前端解析一致），入参兼容两种格式
        javaTimeModule.addDeserializer(LocalDateTime.class, new LenientLocalDateTimeDeserializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 前端/第三方多传字段时不报错（如旧版字段名），提升接口容错性
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
