package com.xiangcetong.ai.service;

import com.xiangcetong.ai.dto.ChatResponse;
import com.xiangcetong.ai.entity.AiChatLog;
import com.xiangcetong.ai.entity.AiConfig;
import com.xiangcetong.ai.entity.AiUsageLog;
import com.xiangcetong.ai.entity.KnowledgeChunk;
import com.xiangcetong.ai.mapper.AiChatLogMapper;
import com.xiangcetong.ai.mapper.AiConfigMapper;
import com.xiangcetong.ai.mapper.AiUsageLogMapper;
import com.xiangcetong.ai.mapper.KnowledgeChunkMapper;
import com.xiangcetong.ai.support.CosineSimilarity;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务。
 * 流程：问题→向量化→相似度检索→获取原文→拼提示词→调大模型。
 * 基于 Spring AI ChatModel（OpenAI 兼容协议，对接硅基流动 DeepSeek-V3）。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 5;
    private static final double MIN_SCORE = 0.25;

    private static final Set<String> GREETING_WORDS = Set.of(
            "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗", "你是谁", "你是",
            "谢谢", "感谢", "辛苦了", "再见", "拜拜", "晚安", "早上好", "下午好", "晚上好"
    );

    private static final Set<String> GREETING_STARTS = Set.of(
            "你好", "您好", "嗨", "哈喽", "hello", "hi", "在", "你是谁", "你是"
    );

    private final ChatModel chatModel;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final AiConfigMapper aiConfigMapper;
    private final AiChatLogMapper chatLogMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JdbcTemplate jdbcTemplate;

    public RagService(ChatModel chatModel,
                      KnowledgeChunkMapper chunkMapper,
                      EmbeddingService embeddingService,
                      AiConfigMapper aiConfigMapper,
                      AiChatLogMapper chatLogMapper,
                      AiUsageLogMapper usageLogMapper,
                      ObjectMapper objectMapper,
                      JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.aiConfigMapper = aiConfigMapper;
        this.chatLogMapper = chatLogMapper;
        this.usageLogMapper = usageLogMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动时回填历史聊天记录中缺失的手机号与用户名（从 sys_user 表同步）。
     */
    @PostConstruct
    public void backfillChatLogUserInfo() {
        try {
            int phones = chatLogMapper.backfillPhone();
            int names = chatLogMapper.backfillUsername();
            if (phones > 0 || names > 0) {
                log.info("历史聊天记录用户信息回填完成: phone={}, username={}", phones, names);
            }
        } catch (Exception e) {
            log.warn("回填聊天记录用户信息失败（不影响正常功能）: {}", e.getMessage());
        }
    }

    /**
     * 执行 RAG 问答（同步、非流式）。
     */
    public ChatResponse chat(String question) {
        if (isSimpleGreeting(question)) {
            log.info("检测到招呼/闲聊，跳过检索直接 LLM");
            String systemPrompt = """
                    你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                    用户是在和你打招呼或闲聊，请友好、热情地回应，然后自然地引导用户询问具体的惠农政策问题。
                    回答尽量控制在 80 字以内。""";
            LLMResult greetResult = callLLM(systemPrompt, "用户和你打招呼，请友好回应。");
            saveChatLog(question, greetResult.answer, false, greetResult.promptTokens, greetResult.completionTokens);
            return new ChatResponse(greetResult.answer, List.of(), false);
        }

        // 元问题（你是谁、用什么模型等）不走 RAG，直接 LLM 回答
        if (isMetaQuestion(question)) {
            log.info("检测到元问题，跳过检索");
            String answer;
            int pt = 0, ct = 0;
            String q = question.strip();

            // 高频元问题硬编码，省 Token 且确保准确
            if (q.contains("大模型") || q.contains("什么模型") || q.contains("哪个模型") || q.contains("什么系统")) {
                answer = "我是乡策通助农政策助手，底层用的是 DeepSeek 大模型（deepseek-flash），专门为乡亲们解答惠农政策问题！";
            } else if (q.contains("能做什么") || q.contains("有什么功能") || q.contains("可以做什么")) {
                answer = "我可以帮您查询各类惠农政策，比如种粮补贴、低保申请、医保补贴、危房改造、农机购置补贴等。您直接告诉我需要了解什么政策就行！";
            } else {
                String systemPrompt = """
                        你是乡策通助农政策助手，基于 DeepSeek 大模型。
                        用户正在问你一个关于你自身的问题，请直接回答，不要引导用户问政策，不要反问用户。
                        回答尽量控制在 80 字以内。""";
                LLMResult mr = callLLM(systemPrompt, q);
                answer = mr.answer;
                pt = mr.promptTokens;
                ct = mr.completionTokens;
            }
            saveChatLog(question, answer, false, pt, ct);
            return new ChatResponse(answer, List.of(), false);
        }

        List<KnowledgeChunk> allChunks = chunkMapper.findAll();

        if (allChunks.isEmpty()) {
            return directChat(question);
        }

        // 尝试向量检索，失败则降级为关键词匹配
        List<CosineSimilarity.ScoredEntry> topK = null;
        boolean embeddingOk = false;
        try {
            float[] questionVector = embeddingService.embed(question);
            if (questionVector != null && questionVector.length > 0) {
                List<CosineSimilarity.VectorEntry> entries = toVectorEntries(allChunks);
                if (!entries.isEmpty()) {
                    topK = CosineSimilarity.topK(questionVector, entries, TOP_K);
                    embeddingOk = true;
                }
            }
        } catch (Exception e) {
            log.warn("向量检索失败，降级为关键词匹配: {}", e.getMessage());
        }

        if (!embeddingOk || topK == null || topK.isEmpty() || topK.get(0).getScore() < MIN_SCORE) {
            topK = keywordSearch(question, allChunks, TOP_K);
        }

        String context;
        List<ChatResponse.SourceInfo> sources = new ArrayList<>();
        boolean fromKnowledge;

        if (topK.isEmpty()) {
            String webResult = webSearch(question);
            if (webResult == null || webResult.isBlank()) {
                log.info("知识库和联网搜索均无结果，使用 LLM 兜底");
                String fallbackPrompt = """
                        你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                        请用通俗易懂的语言回答。如果用户只是打招呼或闲聊，友好回应后引导用户询问具体政策问题。
                        如果用户问了具体政策但你没有相关信息，请如实告知并建议咨询当地农业农村部门。
                        回答尽量控制在 200 字以内。""";
                LLMResult fallbackResult = callLLM(fallbackPrompt, "暂无政策文档参考，请根据你的知识回答。");
                return new ChatResponse(fallbackResult.answer, List.of(), false);
            }
            context = webResult;
            fromKnowledge = false;
        } else {
            context = expandContext(topK, allChunks);
            sources = topK.stream()
                    .map(e -> new ChatResponse.SourceInfo(
                            e.entry.documentId,
                            e.entry.documentTitle,
                            e.entry.text.length() > 100 ? e.entry.text.substring(0, 100) + "..." : e.entry.text
                    ))
                    .collect(Collectors.toList());
            fromKnowledge = true;
        }

        String systemPrompt = """
                你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                请根据用户问题和参考知识库内容作答。
                请严格遵守以下规则：
                1. 只回答用户实际提出的问题，优先使用知识库中与问题最相关的政策内容，忽略不相关的内容。
                2. 必须给出具体的政策细节——如申请条件、补贴标准、所需材料、办理流程。
                3. 每个政策只在开头标明一次【来源：政策名称】，不要每条都重复标注。
                4. 如果知识库中没有相关信息，请如实告知用户，不要编造。
                5. 用通俗易懂的大白话回答，尽量控制在 400 字以内。
                6. 【重要】把参考内容理解后用口语重新组织，不要照抄原文：
                   禁止输出 markdown 符号（#、##、*、**、- 列表符号），
                   禁止输出「政策名称：」「政策内容：」「补贴标准：」这类字段名，
                   也不要原样复制参考资料里的换行结构。直接说人话。
                7. 【重要】如果参考内容与用户问题基本无关（说明知识库暂未收录该类政策），
                   只如实说明「知识库暂未收录」，并建议咨询当地农业农村部门；
                   绝对不要用你自己的知识补充政策名称、补贴金额、申请条件，
                   否则会误导村民。""";

        String userMessage = "用户问题：" + question + "\n\n参考知识库内容：\n\n" + context;
        LLMResult ragResult = callLLM(systemPrompt, userMessage);
        saveChatLog(question, ragResult.answer, fromKnowledge, ragResult.promptTokens, ragResult.completionTokens);
        return new ChatResponse(ragResult.answer, sources, fromKnowledge);
    }

    /**
     * 无知识库时的直接对话（LLM + 联网搜索）。
     */
    private ChatResponse directChat(String question) {
        log.info("知识库为空，使用直接对话模式");
        String webResult = webSearch(question);
        String systemPrompt;
        String context;

        if (webResult != null && !webResult.isBlank()) {
            systemPrompt = """
                    你是乡策通助农政策助手。请根据以下网络搜索结果回答用户问题。
                    如果是时效性强的政策问题，请提示用户以当地政府部门最新公告为准。""";
            context = webResult;
        } else {
            systemPrompt = """
                    你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                    请用通俗易懂的语言回答，如果问题超出你的知识范围，请如实告知。
                    如果是时效性强的政策问题，请提示用户以当地政府部门最新公告为准。
                    回答尽量控制在 300 字以内。""";
            context = "暂无政策文档参考，请根据你的知识回答。";
        }

        LLMResult directResult = callLLM(systemPrompt, context);
        saveChatLog(question, directResult.answer, webResult != null && !webResult.isBlank(),
                directResult.promptTokens, directResult.completionTokens);
        return new ChatResponse(directResult.answer, List.of(), false);
    }

    /**
     * SSE 流式对话：逐 token 返回。
     */
    public String chatStream(String question, java.util.function.Consumer<String> onToken) {
        if (isSimpleGreeting(question)) {
            log.info("流式-检测到招呼/闲聊，跳过检索直接 LLM");
            String systemPrompt = """
                    你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                    用户是在和你打招呼或闲聊，请友好、热情地回应，然后自然地引导用户询问具体的惠农政策问题。
                    回答尽量控制在 80 字以内。""";
            LLMResult greetStreamResult = callLLMStream(systemPrompt, "用户和你打招呼，请友好回应。", onToken);
            saveChatLog(question, greetStreamResult.answer, false, greetStreamResult.promptTokens, greetStreamResult.completionTokens);
            return greetStreamResult.answer;
        }

        // 元问题（你是谁、用什么模型等）不走 RAG，直接 LLM 回答
        if (isMetaQuestion(question)) {
            log.info("流式-检测到元问题，跳过检索");
            String answer;
            int st = 0, ct2 = 0;
            String q = question.strip();

            // 高频元问题硬编码，省 Token 且确保准确
            if (q.contains("大模型") || q.contains("什么模型") || q.contains("哪个模型") || q.contains("什么系统")) {
                answer = "我是乡策通助农政策助手，底层用的是 DeepSeek 大模型（deepseek-flash），专门为乡亲们解答惠农政策问题！";
                onToken.accept(answer);
            } else if (q.contains("能做什么") || q.contains("有什么功能") || q.contains("可以做什么")) {
                answer = "我可以帮您查询各类惠农政策，比如种粮补贴、低保申请、医保补贴、危房改造、农机购置补贴等。您直接告诉我需要了解什么政策就行！";
                onToken.accept(answer);
            } else {
                String systemPrompt = """
                        你是乡策通助农政策助手，基于 DeepSeek 大模型。
                        用户正在问你一个关于你自身的问题，请直接回答，不要引导用户问政策，不要反问用户。
                        回答尽量控制在 80 字以内。""";
                LLMResult metaStreamResult = callLLMStream(systemPrompt, q, onToken);
                answer = metaStreamResult.answer;
                st = metaStreamResult.promptTokens;
                ct2 = metaStreamResult.completionTokens;
            }
            saveChatLog(question, answer, false, st, ct2);
            return answer;
        }

        List<KnowledgeChunk> allChunks = chunkMapper.findAll();

        if (allChunks.isEmpty()) {
            return directChatStream(question, onToken);
        }

        List<CosineSimilarity.ScoredEntry> topK = null;
        try {
            float[] questionVector = embeddingService.embed(question);
            if (questionVector != null && questionVector.length > 0) {
                topK = CosineSimilarity.topK(questionVector, toVectorEntries(allChunks), TOP_K);
            }
        } catch (Exception e) {
            log.warn("流式向量检索失败，降级为关键词匹配: {}", e.getMessage());
        }

        if (topK == null || topK.isEmpty() || topK.get(0).getScore() < MIN_SCORE) {
            topK = keywordSearch(question, allChunks, TOP_K);
        }

        String context;
        if (topK.isEmpty()) {
            String webResult = webSearch(question);
            if (webResult == null || webResult.isBlank()) {
                log.info("流式-知识库和联网搜索均无结果，使用 LLM 兜底");
                String fallbackPrompt = """
                        你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                        请用通俗易懂的语言回答。如果用户只是打招呼或闲聊，友好回应后引导用户询问具体政策问题。
                        如果用户问了具体政策但你没有相关信息，请如实告知并建议咨询当地农业农村部门。
                        回答尽量控制在 200 字以内。""";
                LLMResult fallbackStreamResult = callLLMStream(fallbackPrompt, "暂无政策文档参考，请根据你的知识回答。", onToken);
                saveChatLog(question, fallbackStreamResult.answer, false, fallbackStreamResult.promptTokens, fallbackStreamResult.completionTokens);
                return fallbackStreamResult.answer;
            }
            context = webResult;
        } else {
            context = expandContext(topK, allChunks);
        }

        String systemPrompt = """
                你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                请根据用户问题和参考知识库内容作答。
                请遵守以下规则：
                1. 只回答用户实际提出的问题，优先使用知识库中与问题最相关的政策内容，忽略不相关的内容。
                2. 如果文档或搜索结果中没有相关信息，请如实告知用户，不要编造。
                3. 回答要通俗易懂，避免过于专业的术语。
                4. 每个政策只在开头标明一次来源政策名称，不要每条都重复标注。
                5. 【重要】把参考内容理解后用口语重新组织，不要照抄原文：
                   禁止输出 markdown 符号（#、##、*、**、- 列表符号），
                   禁止输出「政策名称：」「政策内容：」「补贴标准：」这类字段名，
                   也不要原样复制参考资料里的换行结构。直接说人话。
                6. 【重要】如果参考内容与用户问题基本无关（说明知识库暂未收录该类政策），
                   只如实说明「知识库暂未收录」，并建议咨询当地农业农村部门；
                   绝对不要用你自己的知识补充政策名称、补贴金额、申请条件，
                   否则会误导村民。""";

        String userMessage = "用户问题：" + question + "\n\n参考知识库内容：\n\n" + context;
        LLMResult ragStreamResult = callLLMStream(systemPrompt, userMessage, onToken);
        saveChatLog(question, ragStreamResult.answer, !topK.isEmpty(), ragStreamResult.promptTokens, ragStreamResult.completionTokens);
        return ragStreamResult.answer;
    }

    /**
     * 无知识库时的流式直接对话。
     */
    private String directChatStream(String question, java.util.function.Consumer<String> onToken) {
        String webResult = webSearch(question);
        String systemPrompt;
        String context;
        boolean fromKnowledge;

        if (webResult != null && !webResult.isBlank()) {
            systemPrompt = """
                    你是乡策通助农政策助手。请根据以下网络搜索结果回答用户问题。
                    如果是时效性强的政策问题，请提示用户以当地政府部门最新公告为准。""";
            context = webResult;
            fromKnowledge = true;
        } else {
            systemPrompt = """
                    你是乡策通助农政策助手，专门为农村居民提供惠农政策咨询服务。
                    请用通俗易懂的语言回答，如果问题超出你的知识范围，请如实告知。
                    如果是时效性强的政策问题，请提示用户以当地政府部门最新公告为准。
                    回答尽量控制在 300 字以内。""";
            context = "暂无政策文档参考，请根据你的知识回答。";
            fromKnowledge = false;
        }

        LLMResult dcsResult = callLLMStream(systemPrompt, context, onToken);
        saveChatLog(question, dcsResult.answer, fromKnowledge, dcsResult.promptTokens, dcsResult.completionTokens);
        return dcsResult.answer;
    }

    // ===================== Spring AI 调用封装 =====================

    /**
     * 调用大模型（同步），直接 HTTP 调用 DeepSeek API，绕过 Spring AI。
     */
    private LLMResult callLLM(String systemPrompt, String context) {
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            AiConfig config = aiConfigMapper.findFirst();
            String apiKey = resolveChatApiKey(config);
            String apiUrl = config != null && config.getApiUrl() != null && !config.getApiUrl().isBlank()
                    ? config.getApiUrl() : "https://api.deepseek.com/v1";
            String model = config != null && config.getModelName() != null && !config.getModelName().isBlank()
                    ? config.getModelName() : "deepseek-chat";
            double temperature = config != null && config.getTemperature() != null
                    ? config.getTemperature().doubleValue() : 0.7;
            int maxTokens = config != null && config.getMaxTokens() != null
                    ? config.getMaxTokens() : 2048;

            String chatUrl = apiUrl + "/chat/completions";

            Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", context)
            ));

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("调用大模型: url={}, model={}", chatUrl, model);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("大模型 HTTP 响应: status={}", httpResp.statusCode());

            if (httpResp.statusCode() != 200) {
                log.error("大模型 API 返回非 200: status={}, body={}", httpResp.statusCode(),
                        httpResp.body().length() > 300 ? httpResp.body().substring(0, 300) : httpResp.body());
                return new LLMResult("AI 服务异常，请稍后再试。", 0, 0);
            }

            JsonNode respJson = objectMapper.readTree(httpResp.body());
            JsonNode choices = respJson.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                log.error("大模型响应无 choices: {}", httpResp.body().substring(0, Math.min(300, httpResp.body().length())));
                return new LLMResult("AI 服务暂时不可用，请稍后再试。", 0, 0);
            }

            String answer = choices.get(0).get("message").get("content").asText();

            if (answer == null || answer.isBlank()) {
                return new LLMResult("AI 服务暂时不可用，请稍后再试。", 0, 0);
            }

            // 记录 Token 用量
            JsonNode usage = respJson.get("usage");
            if (usage != null) {
                promptTokens = ***("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                completionTokens = ***("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
                int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : promptTokens + completionTokens;

                // DeepSeek 定价（每百万 Token）
                double inputPrice = 1.0;
                double outputPrice = 2.0;
                double cost = (promptTokens / 1_000_000.0) * inputPrice
                        + (completionTokens / 1_000_000.0) * outputPrice;

                AiUsageLog usageLog = new AiUsageLog();
                usageLog.setUserId(UserContext.get().getUserId());
                String uname = UserContext.get().getName();
                usageLog.setUsername(uname != null ? uname : UserContext.get().getPhone());
                usageLog.setModel(model);
                usageLog.setPromptTokens(promptTokens);
                usageLog.setCompletionTokens(completionTokens);
                usageLog.setTotalTokens(totalTokens);
                usageLog.setCost(Math.round(cost * 10000.0) / 10000.0);
                usageLogMapper.insert(usageLog);
            }
            log.info("大模型调用成功, 响应长度={}, promptTokens={}, completionTokens={}",
                    answer.length(), promptTokens, completionTokens);
            return new LLMResult(answer, promptTokens, completionTokens);
        } catch (Exception e) {
            log.error("调用大模型异常", e);
            return new LLMResult("AI 服务异常，请稍后再试。", promptTokens, completionTokens);
        }
    }

    private String resolveChatApiKey(AiConfig config) {
        String fromDb = config != null && config.getApiKey() != null && !config.getApiKey().isBlank()
                ? config.getApiKey() : null;
        if (fromDb != null) return fromDb;
        String fromEnv = System.getenv("DEEPSEEK_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        return "sk-***";
    }

    /**
     * 调用大模型（流式），使用 Spring AI ChatModel.stream()。
     */
    private LLMResult callLLMStream(String systemPrompt, String context,
                                  java.util.function.Consumer<String> onToken) {
        StringBuilder fullAnswer = new StringBuilder();
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            Message systemMsg = new SystemMessage(systemPrompt);
            Message userMsg = new UserMessage(context);
            Prompt prompt = new Prompt(List.of(systemMsg, userMsg));

            Flux<org.springframework.ai.chat.model.ChatResponse> flux = chatModel.stream(prompt);
            // 阻塞收集流式结果
            List<org.springframework.ai.chat.model.ChatResponse> responses = flux.collectList().block();
            if (responses != null) {
                for (org.springframework.ai.chat.model.ChatResponse r : responses) {
                    if (r.getResult() != null && r.getResult().getOutput() != null) {
                        String token = r.getResult().getOutput().getText();
                        if (token != null && !token.isEmpty()) {
                            fullAnswer.append(token);
                            onToken.accept(token);
                        }
                    }
                }
                // 取最后一条有 usage 的响应记录用量
                for (int i = responses.size() - 1; i >= 0; i--) {
                    org.springframework.ai.chat.model.ChatResponse r = responses.get(i);
                    if (r.getMetadata() != null && r.getMetadata().getUsage() != null) {
                        Usage usage = r.getMetadata().getUsage();
                        promptTokens = ***();
                        completionTokens = ***();
                        saveUsageLog(r);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("流式调用大模型异常", e);
            onToken.accept("\n[AI 服务异常，请稍后再试]");
        }
        return new LLMResult(fullAnswer.toString(), promptTokens, completionTokens);
    }

    // ===================== 联网搜索（保留手写 HttpClient） =====================

    /**
     * 联网搜索（简易版，使用 DuckDuckGo 免费搜索）。
     */
    private String webSearch(String question) {
        try {
            String encoded = java.net.URLEncoder.encode(question + " 惠农政策 农村", "UTF-8");
            String url = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "XiangCeTong/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                StringBuilder sb = new StringBuilder();
                JsonNode abstractText = root.get("AbstractText");
                if (abstractText != null && !abstractText.asText().isBlank()) {
                    sb.append(abstractText.asText()).append("\n\n");
                }
                JsonNode related = root.get("RelatedTopics");
                if (related != null && related.isArray()) {
                    int count = 0;
                    for (JsonNode r : related) {
                        if (count >= 3) break;
                        JsonNode text = r.get("Text");
                        if (text != null && !text.asText().isBlank()) {
                            sb.append(text.asText()).append("\n");
                            count++;
                        }
                    }
                }
                return sb.toString().isBlank() ? null : sb.toString().strip();
            }
        } catch (Exception e) {
            log.warn("联网搜索失败: {}", e.getMessage());
        }
        return null;
    }

    // ===================== 关键词兜底检索 =====================

    /**
     * 关键词兜底检索：对问题分词后在分块文本中匹配。
     * 2026-10-17：实现下沉到 {@link com.xiangcetong.ai.support.KeywordMatcher}，
     * 便于单测覆盖，并修复「泛词命中全部分块、Top-K 实际随机」的缺陷。
     */
    private List<CosineSimilarity.ScoredEntry> keywordSearch(
            String question, List<KnowledgeChunk> chunks, int topK) {
        return com.xiangcetong.ai.support.KeywordMatcher.search(question, chunks, topK);
    }

    // ===================== 上下文扩展 =====================

    /**
     * 扩展上下文：检索命中块后，自动带上同文档前后相邻块，避免政策内容被截断。
     */
    private String expandContext(List<CosineSimilarity.ScoredEntry> topK,
                                  List<KnowledgeChunk> allChunks) {
        Set<Long> includedChunkIds = new HashSet<>();
        List<KnowledgeChunk> expanded = new ArrayList<>();

        for (CosineSimilarity.ScoredEntry e : topK) {
            Long docId = e.entry.documentId;
            Long chunkId = e.entry.chunkId;
            if (!includedChunkIds.add(chunkId)) continue;

            KnowledgeChunk core = findChunk(allChunks, chunkId);
            if (core == null) {
                KnowledgeChunk fallback = new KnowledgeChunk();
                fallback.setChunkText(e.entry.text);
                expanded.add(fallback);
                continue;
            }
            int coreIndex = core.getChunkIndex();

            KnowledgeChunk prev = findChunkByDocAndIndex(allChunks, docId, coreIndex - 1);
            KnowledgeChunk next = findChunkByDocAndIndex(allChunks, docId, coreIndex + 1);

            if (prev != null && includedChunkIds.add(prev.getId())) {
                expanded.add(prev);
            }
            expanded.add(core);
            if (next != null && includedChunkIds.add(next.getId())) {
                expanded.add(next);
            }
        }

        expanded.sort((a, b) -> {
            int cmp = Long.compare(a.getDocumentId(), b.getDocumentId());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getChunkIndex(), b.getChunkIndex());
        });

        return expanded.stream()
                .map(c -> {
                    String title = c.getDocumentTitle() != null && !c.getDocumentTitle().isBlank()
                            ? c.getDocumentTitle() : "政策文件";
                    return "【来源：" + title + "】\n" + c.getChunkText();
                })
                .collect(Collectors.joining("\n\n"));
    }

    private KnowledgeChunk findChunk(List<KnowledgeChunk> chunks, Long id) {
        return chunks.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    private KnowledgeChunk findChunkByDocAndIndex(List<KnowledgeChunk> chunks, Long docId, int index) {
        return chunks.stream()
                .filter(c -> c.getDocumentId().equals(docId) && c.getChunkIndex() == index)
                .findFirst().orElse(null);
    }

    // ===================== 向量数据转换 =====================

    /**
     * 将数据库中的 KnowledgeChunk 列表转为 VectorEntry 列表。
     */
    private List<CosineSimilarity.VectorEntry> toVectorEntries(List<KnowledgeChunk> chunks) {
        List<CosineSimilarity.VectorEntry> entries = new ArrayList<>();
        for (KnowledgeChunk ck : chunks) {
            try {
                List<Double> values = objectMapper.readValue(
                        ck.getEmbedding(),
                        new TypeReference<List<Double>>() {}
                );
                float[] vec = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vec[i] = values.get(i).floatValue();
                }
                entries.add(new CosineSimilarity.VectorEntry(
                        ck.getDocumentId(), ck.getId(), ck.getChunkText(), vec,
                        ck.getDocumentTitle()));
            } catch (Exception e) {
                log.warn("解析向量失败: chunkId={}, err={}, embedding前50字符={}",
                        ck.getId(), e.getMessage(),
                        ck.getEmbedding() != null && ck.getEmbedding().length() > 50
                                ? ck.getEmbedding().substring(0, 50) : ck.getEmbedding());
            }
        }
        return entries;
    }

    // ===================== LLM 调用结果（携带 Token 信息） =====================

    /**
     * LLM 调用结果：答案 + Token 消耗。
     */
    private static class LLMResult {
        final String answer;
        final int promptTokens;
        final int completionTokens;

        LLMResult(String answer, int promptTokens, int completionTokens) {
            this.answer = answer;
            this.promptTokens = ***;
            this.completionTokens = ***;
        }
    }

    // ===================== 日志记录 =====================

    /**
     * 记录 AI 对话日志。
     */
    private void saveChatLog(String question, String answer, boolean fromKnowledge,
                             int promptTokens, int completionTokens) {
        try {
            UserContext.LoginUser user = UserContext.get();
            Long userId = user != null ? user.getUserId() : 0L;
            String username = user != null && user.getName() != null ? user.getName()
                    : (user != null ? user.getPhone() : "匿名用户");
            String phone = user != null && user.getPhone() != null && !user.getPhone().isBlank()
                    ? user.getPhone()
                    : lookupPhone(userId);

            AiChatLog log = new AiChatLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setPhone(phone != null ? phone : "");
            log.setQuestion(question);
            log.setAnswer(answer != null && answer.length() > 500 ? answer.substring(0, 500) : answer);
            log.setFromKnowledge(fromKnowledge ? 1 : 0);
            log.setModel(aiConfigMapper.findFirst() != null ? aiConfigMapper.findFirst().getModelName() : "deepseek-ai/DeepSeek-V3");
            log.setPromptTokens(promptTokens);
            log.setCompletionTokens(completionTokens);
            chatLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("记录对话日志失败: {}", e.getMessage());
        }
    }

    /**
     * 从 sys_user 表反查手机号（当 JWT 上下文 phone 为空时兜底）。
     */
    private String lookupPhone(Long userId) {
        if (userId == null || userId == 0L) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT phone FROM sys_user WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            log.debug("查询用户手机号失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 记录 Token 用量日志（从 Spring AI ChatResponse 中提取）。
     */
    private void saveUsageLog(org.springframework.ai.chat.model.ChatResponse response) {
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) return;

            UserContext.LoginUser user = UserContext.get();
            int promptTokens = usage.getPromptTokens();
            int completionTokens = usage.getCompletionTokens();
            int totalTokens = usage.getTotalTokens();

            AiConfig config = aiConfigMapper.findFirst();
            String model = config != null ? config.getModelName() : "deepseek-ai/DeepSeek-V3";

            // DeepSeek 定价（每百万 Token）
            double inputPrice = "deepseek-reasoner".equals(model) ? 4.0 : 1.0;
            double outputPrice = "deepseek-reasoner".equals(model) ? 16.0 : 2.0;
            double cost = (promptTokens / 1_000_000.0) * inputPrice
                    + (completionTokens / 1_000_000.0) * outputPrice;

            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setUserId(user != null ? user.getUserId() : 0L);
            usageLog.setUsername(user != null && user.getName() != null ? user.getName()
                    : (user != null ? user.getPhone() : "匿名用户"));
            usageLog.setModel(model);
            usageLog.setPromptTokens(promptTokens);
            usageLog.setCompletionTokens(completionTokens);
            usageLog.setTotalTokens(totalTokens);
            usageLog.setCost(Math.round(cost * 10000.0) / 10000.0);
            usageLogMapper.insert(usageLog);

            log.info("Token 用量记录: model={}, prompt={}, completion={}, total={}, cost={}",
                    model, promptTokens, completionTokens, totalTokens, usageLog.getCost());
        } catch (Exception e) {
            log.warn("记录用量日志失败: {}", e.getMessage());
        }
    }

    // ===================== 招呼检测 =====================

    /**
     * 判断是否为简单招呼/闲聊，命中则跳过联网搜索加快响应。
     */
    private boolean isSimpleGreeting(String question) {
        if (question == null || question.isBlank()) return true;
        String q = question.strip();
        if (q.length() <= 10) {
            for (String gw : GREETING_WORDS) {
                if (q.equals(gw)) return true;
            }
        }
        for (String gs : GREETING_STARTS) {
            if (q.startsWith(gs) && q.length() <= 15) return true;
        }
        return false;
    }

    private static final Set<String> META_KEYWORDS = Set.of(
            "什么模型", "哪个模型", "大模型", "什么系统", "哪个系统",
            "是什么", "你是谁", "你叫什么", "你的名字", "怎么用", "怎么使用",
            "能做什么", "可以做什么", "有什么功能", "功能介绍", "gpt", "chatgpt",
            "AI", "ai", "人工智能"
    );

    private static final Set<String> META_STARTS = Set.of(
            "你是", "你是谁", "你叫", "你用的", "你用什么", "你用的是",
            "你基于", "你背后", "你怎么"
    );

    /**
     * 判断是否为关于 AI 自身的元问题（如"你是什么模型"、"你怎么工作"）。
     * 命中则跳过知识库检索，直接由 LLM 回答。
     */
    private boolean isMetaQuestion(String question) {
        if (question == null || question.isBlank()) return false;
        String q = question.strip();
        for (String kw : META_KEYWORDS) {
            if (q.contains(kw)) return true;
        }
        for (String ms : META_STARTS) {
            if (q.startsWith(ms)) return true;
        }
        return false;
    }
}