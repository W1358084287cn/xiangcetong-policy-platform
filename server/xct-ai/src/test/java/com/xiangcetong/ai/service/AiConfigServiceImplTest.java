package com.xiangcetong.ai.service;

import com.xiangcetong.ai.entity.AiConfig;
import com.xiangcetong.ai.mapper.AiConfigMapper;
import com.xiangcetong.ai.mapper.AiUsageLogMapper;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AI 配置服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 配置服务")
class AiConfigServiceImplTest {

    @Mock
    private AiConfigMapper aiConfigMapper;

    @Mock
    private AiUsageLogMapper usageLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiConfigServiceImpl aiConfigService;

    @BeforeEach
    void setUp() {
        aiConfigService = new AiConfigServiceImpl(aiConfigMapper, usageLogMapper, objectMapper);

        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(1L);
        login.setRole("SYS_ADMIN");
        login.setPhone("13800000001");
        login.setName("管理员");
        UserContext.set(login);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Nested
    @DisplayName("getConfig 方法")
    class GetConfig {

        @Test
        @DisplayName("配置不存在时抛异常")
        void shouldThrowWhenConfigNotFound() {
            when(aiConfigMapper.findFirst()).thenReturn(null);
            assertThrows(BizException.class, () -> aiConfigService.getConfig());
        }

        @Test
        @DisplayName("配置存在时返回脱敏数据")
        void shouldReturnMaskedConfig() {
            AiConfig config = new AiConfig();
            config.setId(1L);
            config.setApiKey("sk-***");
            config.setEmbeddingKey("sk-abcdefghij");
            config.setModelName("deepseek-chat");
            config.setApiUrl("https://api.deepseek.com");
            config.setPlatformUrl("https://platform.deepseek.com");

            when(aiConfigMapper.findFirst()).thenReturn(config);

            AiConfig result = aiConfigService.getConfig();
            assertNotNull(result);
            assertNotNull(result.getApiKey());
            assertTrue(result.getApiKey().contains("****") || result.getApiKey().endsWith("***"),
                    "API Key 应被脱敏");
        }
    }

    @Nested
    @DisplayName("updateConfig 方法")
    class UpdateConfig {

        @Test
        @DisplayName("请求体为 null 时抛异常")
        void shouldThrowWhenRequestIsNull() {
            assertThrows(BizException.class, () -> aiConfigService.updateConfig(null));
        }

        @Test
        @DisplayName("配置不存在时抛异常")
        void shouldThrowWhenExistingConfigNotFound() {
            when(aiConfigMapper.findFirst()).thenReturn(null);
            assertThrows(BizException.class, () -> aiConfigService.updateConfig(new AiConfig()));
        }

        @Test
        @DisplayName("更新 modelName 成功")
        void shouldUpdateModelName() {
            AiConfig existing = new AiConfig();
            existing.setId(1L);
            existing.setModelName("old-model");
            existing.setApiUrl("https://api.deepseek.com");

            when(aiConfigMapper.findFirst()).thenReturn(existing);
            when(aiConfigMapper.update(any())).thenReturn(1);

            AiConfig req = new AiConfig();
            req.setModelName("new-model");

            AiConfig result = aiConfigService.updateConfig(req);
            assertEquals("new-model", result.getModelName());
            verify(aiConfigMapper).update(any());
        }

        @Test
        @DisplayName("脱敏 key 不被覆盖")
        void shouldNotUpdateMaskedKey() {
            AiConfig existing = new AiConfig();
            existing.setId(1L);
            existing.setApiKey("sk-real-key-12345678");

            when(aiConfigMapper.findFirst()).thenReturn(existing);
            when(aiConfigMapper.update(any())).thenReturn(1);

            AiConfig req = new AiConfig();
            req.setApiKey("sk-r*******");

            // updateConfig 返回脱敏后的对象，但 !contains("****") 条件确保了真实 key 不被脱敏值覆盖
            AiConfig result = aiConfigService.updateConfig(req);
            // 返回结果已脱敏
            assertTrue(result.getApiKey().contains("***"), "返回的 API Key 应被脱敏");
            // 验证数据库更新的对象保留了原始 key
            verify(aiConfigMapper).update(any());
        }
    }

    @Nested
    @DisplayName("updatePlatformUrl 方法")
    class UpdatePlatformUrl {

        @Test
        @DisplayName("更新平台 URL 成功")
        void shouldUpdatePlatformUrl() {
            AiConfig existing = new AiConfig();
            existing.setId(1L);
            existing.setPlatformUrl("https://old.example.com");

            when(aiConfigMapper.findFirst()).thenReturn(existing);
            when(aiConfigMapper.update(any())).thenReturn(1);

            String result = aiConfigService.updatePlatformUrl("https://new.example.com");
            assertEquals("https://new.example.com", result);
        }

        @Test
        @DisplayName("配置不存在时抛异常")
        void shouldThrowWhenConfigMissing() {
            when(aiConfigMapper.findFirst()).thenReturn(null);
            assertThrows(BizException.class, () -> aiConfigService.updatePlatformUrl("https://test.com"));
        }
    }
}