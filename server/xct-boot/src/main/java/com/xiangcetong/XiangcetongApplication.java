package com.xiangcetong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 乡策通后端启动类。
 * 扫描 MyBatis Mapper，启动内嵌 Tomcat（默认 8080）。
 * 排除 Spring AI OpenAI 自动配置，改用 AiClientConfiguration 手动管理。
 */
@SpringBootApplication(exclude = {
    OpenAiChatAutoConfiguration.class,
    OpenAiEmbeddingAutoConfiguration.class,
    OpenAiAudioSpeechAutoConfiguration.class,
    OpenAiAudioTranscriptionAutoConfiguration.class,
    OpenAiImageAutoConfiguration.class,
    OpenAiModerationAutoConfiguration.class
})
@MapperScan("com.xiangcetong.**.mapper")
public class XiangcetongApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 容器
        SpringApplication.run(XiangcetongApplication.class, args);
    }
}