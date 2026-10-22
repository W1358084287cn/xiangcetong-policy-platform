package com.xiangcetong.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.ai.dto.*;
import com.xiangcetong.ai.entity.AiChatLog;
import com.xiangcetong.ai.entity.AiConversation;
import com.xiangcetong.ai.mapper.AiChatLogMapper;
import com.xiangcetong.ai.mapper.AiConversationMapper;
import com.xiangcetong.ai.mapper.AiConfigMapper;
import com.xiangcetong.ai.support.AgentToolRegistry;
import com.xiangcetong.common.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AI 管家核心服务——意图识别 + 工具调用编排 + 申报流程管理。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentToolRegistry registry;
    private final AiConversationMapper convMapper;
    private final AiChatLogMapper chatLogMapper;
    private final AiConfigMapper aiConfigMapper;
    private final JdbcTemplate jdbc;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    /** 申报草稿上下文（conversationId → Draft） */
    private final Map<Long, ApplyDraft> drafts = new ConcurrentHashMap<>();

    /** 走 AI 兜底的最小阈值：当关键词匹配到但置信度不够时，交给 AI */
    private static final int AI_FALLBACK_MIN_TOKENS = 0;

    // ==================== 2026-10-18 政策列表防刷屏治理 ====================
    /** AI 文本回复里最多逐条罗列的政策数（其余折叠进卡片，按需展开） */
    private static final int POLICY_TEXT_PREVIEW = 10;
    /** 政策卡片默认展示的「相关」项上限（命中+部分匹配），其余折叠 */
    private static final int POLICY_CARD_PREVIEW_LIMIT = 20;
    /** 匹配等级排序权重：命中最优，资料待完善/不匹配垫底 */
    private static final Map<String, Integer> MATCH_LEVEL_RANK = Map.of(
            "命中", 0, "部分匹配", 1, "适用", 2, "资料待完善", 3, "不匹配", 4);

    /** 按等级稳定排序（命中 > 部分匹配 > 适用 > 资料待完善 > 不匹配），同档保持原序 */
    private static List<Map<String, Object>> sortPoliciesByLevel(List<Map<String, Object>> policies) {
        List<Map<String, Object>> sorted = new ArrayList<>(policies);
        sorted.sort((a, b) -> Integer.compare(
                MATCH_LEVEL_RANK.getOrDefault(String.valueOf(a.get("matchLevel")), 3),
                MATCH_LEVEL_RANK.getOrDefault(String.valueOf(b.get("matchLevel")), 3)));
        return sorted;
    }

    private static int countLevel(List<Map<String, Object>> policies, String level) {
        return (int) policies.stream().filter(p -> level.equals(p.get("matchLevel"))).count();
    }

    /**
     * 构造政策列表卡片：全量下发 + summary 统计，由前端按需折叠。
     * 数据量上来后（演示库已有 62 项）逐条平铺会严重刷屏。
     */
    private Map<String, Object> buildPolicyListCard(List<Map<String, Object>> policies, List<String> suggestions) {
        List<Map<String, Object>> sorted = sortPoliciesByLevel(policies);
        int hit = countLevel(sorted, "命中");
        int partial = countLevel(sorted, "部分匹配");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", sorted.size());
        summary.put("hit", hit);
        summary.put("partial", partial);
        summary.put("other", sorted.size() - hit - partial);
        summary.put("previewLimit", POLICY_CARD_PREVIEW_LIMIT);
        Map<String, Object> cardData = new LinkedHashMap<>();
        cardData.put("type", "POLICY_LIST");
        cardData.put("policies", sorted);
        cardData.put("summary", summary);
        cardData.put("suggestions", suggestions);
        return cardData;
    }

    public AgentService(AgentToolRegistry registry, AiConversationMapper convMapper,
                        AiChatLogMapper chatLogMapper, AiConfigMapper aiConfigMapper,
                        JdbcTemplate jdbc, RagService ragService, ObjectMapper objectMapper) {
        this.registry = registry;
        this.convMapper = convMapper;
        this.chatLogMapper = chatLogMapper;
        this.aiConfigMapper = aiConfigMapper;
        this.jdbc = jdbc;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initTables() {
        try {
            convMapper.createTableIfNotExists();
            // 兼容旧表：补充 draft_data 列（MySQL 8.0+ 不支持 IF NOT EXISTS，用 try-catch 兜底）
            try {
                jdbc.execute("ALTER TABLE ai_conversation ADD COLUMN draft_data TEXT DEFAULT NULL COMMENT '申报草稿JSON' AFTER last_action");
            } catch (Exception ignored) {
                // 列已存在，忽略
            }
            log.info("AI 管家数据库表初始化完成");
        } catch (Exception e) {
            log.warn("AI 管家表初始化失败（不影响已有功能）: {}", e.getMessage());
        }
    }

    // ==================== 欢迎页 ====================

    public WelcomeVO getWelcome(Long userId) {
        UserContext.LoginUser user = UserContext.get();
        String name = user != null && user.getName() != null ? user.getName() : "村民";
        String greeting = buildGreeting(name);

        WelcomeVO.Summary summary = new WelcomeVO.Summary();
        List<Map<String, Object>> matched = registry.matchPolicies(userId);
        // 资料为空时所有政策都标记为"资料待完善"供用户浏览，不计入"不匹配"
        summary.setMatchedPolicies((int) matched.stream()
                .filter(p -> !"不匹配".equals(p.get("matchLevel"))).count());
        summary.setExpiringPolicies(registry.countExpiringPolicies(userId));
        summary.setPendingApplies(registry.countPendingApplies(userId));
        summary.setProfileCompleteness(registry.profileCompleteness(userId));

        String[] suggestions = buildSuggestions(summary);
        WelcomeVO vo = WelcomeVO.create(greeting, summary, suggestions);

        // 构建导航链接
        List<WelcomeVO.QuickLink> links = new ArrayList<>();
        links.add(new WelcomeVO.QuickLink("匹配政策", "/policies", "🎯"));
        links.add(new WelcomeVO.QuickLink("申报进度", "/mine/applies", "📂"));
        links.add(new WelcomeVO.QuickLink("个人资料", "/mine", "👤"));
        if (summary.getMatchedPolicies() > 0) {
            links.add(new WelcomeVO.QuickLink("政策搜索", "/policies", "🔍"));
        }
        vo.setQuickLinks(links.toArray(new WelcomeVO.QuickLink[0]));
        return vo;
    }

    // ==================== 同步对话 ====================

    public AgentResponse chat(AgentRequest req, Long userId) {
        Long convId = ensureConversation(req.getConversationId(), userId);
        String question = req.getMessage().trim();

        // 检查是否有进行中的申报流程（先查内存，再查数据库恢复）
        ApplyDraft draft = drafts.get(convId);
        if (draft == null) {
            draft = restoreDraft(convId);
        }

        if (draft != null && isApplyContextMessage(question)) {
            return handleApplyDraft(convId, userId, draft, question);
        }

        // 意图识别
        AgentAction action = intentDetect(question, draft != null);

        // 无草稿时用户说"确认提交" → 友好提示，不走 AI
        if (isSubmitWithoutDraft(action, draft, question)) {
            String answer = "当前没有进行中的申报流程。请先说「帮我申报XX政策」开始新的申报哦！";
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.SUBMIT_APPLY, question, answer, null, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.SUBMIT_APPLY, answer, null, 0);
        }

        // 开始申报流程
        if (action == AgentAction.START_APPLY && draft == null) {
            return startApply(convId, userId, question);
        }

        // 取消操作
        if (action == AgentAction.CANCEL_ACTION && draft != null) {
            drafts.remove(convId);
            clearDraft(convId);
            String answer = "已取消当前申报流程。有什么其他需要帮您的吗？";
            saveAgentChatLog(userId, convId, action, question, answer, null, false, 0, 0);
            return AgentResponse.of(convId, null, action, answer, null, 0);
        }

        // 执行动作
        return executeAction(convId, userId, question, action);
    }

    // ==================== SSE 流式对话 ====================

    public void chatStream(AgentRequest req, Long userId, SseEmitter emitter) {
        Long convId = ensureConversation(req.getConversationId(), userId);
        String question = req.getMessage().trim();

        try {
            // 检查是否有进行中的申报流程（先查内存，再查数据库恢复）
            ApplyDraft draft = drafts.get(convId);
            if (draft == null) {
                draft = restoreDraft(convId);
            }

            // 申报上下文中 → 同步处理
            if (draft != null && isApplyContextMessage(question)) {
                AgentResponse resp = handleApplyDraft(convId, userId, draft, question);
                sendSseEvent(emitter, "action", mapOf("agentAction", resp.getAgentAction(), "message", "正在处理..."));
                sendSseEvent(emitter, "token", resp.getAnswer());
                if (resp.getData() != null) {
                    sendSseEvent(emitter, "card", resp.getData());
                }
                // 提交成功或取消后发送快捷回复建议
                if (AgentAction.SUBMIT_APPLY.name().equals(resp.getAgentAction())) {
                    sendSseEvent(emitter, "quick", mapOf("suggestions",
                            List.of("我的申报", "匹配政策", "我要申报别的"),
                            "navigateTo", "/apply/detail/" +
                                    (resp.getData() != null ? String.valueOf(
                                            ((Map<String, Object>) resp.getData()).getOrDefault("applyId", "")) : "")));
                }
                sendSseEvent(emitter, "done", mapOf("conversationId", convId, "messageId", resp.getMessageId(), "totalTokens", resp.getTotalTokens()));
                emitter.complete();
                return;
            }

            AgentAction action = intentDetect(question, draft != null);

            // 无草稿时用户说"确认提交" → 友好提示，不走 AI
            if (isSubmitWithoutDraft(action, draft, question)) {
                String answer = "当前没有进行中的申报流程。请先说「帮我申报XX政策」开始新的申报哦！";
                Long msgId = saveAgentChatLog(userId, convId, AgentAction.SUBMIT_APPLY, question, answer, null, false, 0, 0);
                sendSseEvent(emitter, "action", mapOf("agentAction", AgentAction.SUBMIT_APPLY.name(), "message", ""));
                sendSseEvent(emitter, "token", answer);
                sendSseEvent(emitter, "done", mapOf("conversationId", convId, "messageId", msgId));
                emitter.complete();
                return;
            }

            // 开始申报 → 同步
            if (action == AgentAction.START_APPLY && draft == null) {
                AgentResponse resp = startApply(convId, userId, question);
                sendSseEvent(emitter, "action", mapOf("agentAction", resp.getAgentAction(), "message", "正在为您准备申报材料..."));
                sendSseEvent(emitter, "token", resp.getAnswer());
                if (resp.getData() != null) {
                    sendSseEvent(emitter, "card", resp.getData());
                }
                sendSseEvent(emitter, "done", mapOf("conversationId", convId, "messageId", resp.getMessageId(), "totalTokens", resp.getTotalTokens()));
                emitter.complete();
                return;
            }

            // 取消 → 同步
            if (action == AgentAction.CANCEL_ACTION && draft != null) {
                drafts.remove(convId);
                clearDraft(convId);
                String answer = "已取消当前申报流程。有什么其他需要帮您的吗？";
                Long msgId = saveAgentChatLog(userId, convId, action, question, answer, null, false, 0, 0);
                sendSseEvent(emitter, "action", mapOf("agentAction", action.name(), "message", ""));
                sendSseEvent(emitter, "token", answer);
                sendSseEvent(emitter, "done", mapOf("conversationId", convId, "messageId", msgId));
                emitter.complete();
                return;
            }

            // 需要 AI 的 → 流式代理到 RagService
            if (action == AgentAction.FREE_CHAT || action == AgentAction.SEARCH_KB) {
                streamFreeChat(convId, userId, question, action, emitter);
                return;
            }

            // 其他动作 → 同步执行 + SSE 输出
            AgentResponse resp = executeAction(convId, userId, question, action);
            sendSseEvent(emitter, "action", mapOf("agentAction", resp.getAgentAction(), "message", "正在" + actionLabel(action) + "..."));
            sendSseEvent(emitter, "token", resp.getAnswer());
            if (resp.getData() != null) {
                sendSseEvent(emitter, "card", resp.getData());
            }
            // 发送快捷操作建议
            sendQuickEvent(emitter, action, resp);
            sendSseEvent(emitter, "done", mapOf("conversationId", convId, "messageId", resp.getMessageId(), "totalTokens", resp.getTotalTokens()));

        } catch (Exception e) {
            log.error("Agent SSE 异常: {}", e.getMessage());
            sendSseEvent(emitter, "error", mapOf("message", "AI 管家暂时不可用，请稍后再试"));
        } finally {
            emitter.complete();
        }
    }

    // ==================== 意图识别（关键词优先） ====================

    private AgentAction intentDetect(String msg, boolean inApplyContext) {
        String m = msg.toLowerCase().strip();

        // 申报流程中的特殊意图
        if (inApplyContext) {
            if (matchAny(m, "确认提交", "确认申报", "提交申请", "可以提交", "好了", "确认", "提交")) {
            return AgentAction.SUBMIT_APPLY;
        }
        if (matchAny(m, "修改", "改成", "改一下", "不是这个", "写错了", "调整")) {
            return AgentAction.MODIFY_FIELD;
        }
            if (matchAny(m, "取消", "算了", "不申报了", "不申请了", "换个政策", "换一个")) {
                return AgentAction.CANCEL_ACTION;
            }
            if (matchAny(m, "怎么填", "这个怎么填", "不会填", "帮我填")) {
                return AgentAction.FILL_FIELD;
            }
            // 其他消息默认为填写字段值
            return AgentAction.FILL_FIELD;
        }

        // 个人资料
        if (matchAny(m, "我的资料", "个人信息", "我的信息", "看看我的资料", "我的画像", "我的档案", "帮我查查我的资料", "看看我的信息")) {
            return AgentAction.VIEW_PROFILE;
        }

        // 政策匹配
        if (matchAny(m, "适合我的政策", "适合什么政策", "适合啥政策", "适合的政策", "能申报什么",
                "我能申报", "我有资格", "我有什么补贴", "哪些补贴", "匹配政策", "推荐政策",
                "我能领什么", "有什么补贴", "哪些政策", "帮我匹配", "看看我适合",
                "给我推荐", "我可以申请", "有没有适合", "看下我能", "帮我看看",
                "有啥政策", "有什么政策", "有哪些政策", "看看有啥", "什么政策",
                "帮我找", "帮我查下", "看看有", "有啥补贴", "有些什么政策")) {
            return AgentAction.MATCH_POLICY;
        }

        // 开始申报
        if (matchAny(m, "帮我申报", "我要申请", "帮我提交", "帮我办理", "申报这个",
                "申请补贴", "开始申报", "帮我报", "我要申报", "申请", "申报")) {
            return AgentAction.START_APPLY;
        }

        // 查看申报进度
        if (matchAny(m, "我的申报", "申报进度", "审核到哪", "申请记录", "查一下进度",
                "通过没", "驳回", "什么时候批", "审核状态", "进度", "申请列表")) {
            return AgentAction.MY_APPLIES;
        }

        // 搜索知识库
        if (matchAny(m, "怎么申报", "什么是", "条件是什么", "标准是什么", "补贴多少",
                "需要什么材料", "搜索", "查找", "帮查", "介绍",
                "政策搜索", "搜索政策", "知识库", "帮我查一下", "帮我查查",
                "了解一下", "什么是政策", "政策解读")) {
            return AgentAction.SEARCH_KB;
        }

        // 政策详情（通过标题关键词）
        if (matchAny(m, "政策详情", "具体条件", "详细说明", "政策内容")) {
            return AgentAction.POLICY_DETAIL;
        }

        return AgentAction.FREE_CHAT;
    }

    private boolean isApplyContextMessage(String msg) {
        String m = msg.strip();
        return !matchAny(m.toLowerCase(), "匹配政策", "我的资料", "我的申报", "搜索", "查找");
    }

    // ==================== 动作执行 ====================

    private AgentResponse executeAction(Long convId, Long userId, String question, AgentAction action) {
        return switch (action) {
            case VIEW_PROFILE -> handleViewProfile(convId, userId, question);
            case MATCH_POLICY -> handleMatchPolicy(convId, userId, question);
            case MY_APPLIES -> handleMyApplies(convId, userId, question);
            case POLICY_DETAIL -> handlePolicyDetail(convId, userId, question);
            case APPLY_STATUS -> handleApplyStatus(convId, userId, question);
            default -> handleFreeChat(convId, userId, question); // 包括 FREE_CHAT, SEARCH_KB
        };
    }

    // -------------------- 查看资料 --------------------

    private AgentResponse handleViewProfile(Long convId, Long userId, String question) {
        Map<String, Object> profile = registry.getVillagerProfile(userId);
        if (profile == null) {
            String answer = "暂时没有您的个人资料。您可以去「我的-个人资料」页面完善信息，这样我能更好地帮您匹配政策。";
            Map<String, Object> cardData = Map.of("type", "USER_PROFILE", "navigateTo", "/mine");
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.VIEW_PROFILE, question, answer, cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.VIEW_PROFILE, answer, cardData, 0);
        }

        int completeness = registry.profileCompleteness(userId);
        StringBuilder sb = new StringBuilder("您好！以下是您在系统中的个人资料");
        if (completeness < 100) {
            sb.append("（完善度 ").append(completeness).append("%）：\n");
        } else {
            sb.append("：\n");
        }
        appendField(sb, "姓名", profile.get("name"));
        appendField(sb, "手机号", profile.get("phone"));
        appendField(sb, "地区", profile.get("location"));
        appendField(sb, "种植类型", profile.get("plantType"));
        appendField(sb, "种植面积", profile.get("plantArea"), "亩");
        appendField(sb, "经营主体", profile.get("subjectType"));
        appendField(sb, "银行卡", profile.get("bankCard"));
        if (Boolean.TRUE.equals(profile.get("isVeteranFamily"))) sb.append("· 🎖 退役军人家庭\n");
        if (Boolean.TRUE.equals(profile.get("isMonitorHousehold"))) sb.append("· 📋 脱贫监测户\n");
        if (completeness < 100) {
            sb.append("\n💡 点击下方卡片可跳转到个人资料页完善信息，这样我能帮您更精准地匹配政策。");
        }

        Map<String, Object> cardData = new LinkedHashMap<>();
        cardData.put("type", "USER_PROFILE");
        cardData.put("profile", profile);
        cardData.put("completeness", completeness);
        cardData.put("navigateTo", "/mine");
        cardData.put("navigateLabel", "完善资料 →");

        Long msgId = saveAgentChatLog(userId, convId, AgentAction.VIEW_PROFILE, question, sb.toString(), cardData, false, 0, 0);
        return AgentResponse.of(convId, msgId, AgentAction.VIEW_PROFILE, sb.toString(), cardData, 0);
    }

    // -------------------- 匹配政策 --------------------

    private AgentResponse handleMatchPolicy(Long convId, Long userId, String question) {
        List<Map<String, Object>> policies = sortPoliciesByLevel(registry.matchPolicies(userId));

        if (policies.isEmpty()) {
            String answer = "暂未找到适合您的政策。建议您先完善个人资料（种植类型、面积等），这样我能更精准地为您匹配。";
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.MATCH_POLICY, question, answer, null, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.MATCH_POLICY, answer, null, 0);
        }

        // 2026-10-18 防刷屏治理：政策总量上来后（演示库 62 项）逐条罗列会刷出
        // 几十行文本。文本只展示最相关的前 POLICY_TEXT_PREVIEW 条，
        // 其余折叠进下方卡片，用户点「展开其余 N 项」按需查看。
        int preview = Math.min(policies.size(), POLICY_TEXT_PREVIEW);
        StringBuilder sb = new StringBuilder("根据您的资料，为您匹配到 " + policies.size() + " 项惠农政策");
        sb.append(policies.size() > preview
                ? "，先为您展示最相关的 " + preview + " 项：\n\n"
                : "：\n\n");
        for (int i = 0; i < preview; i++) {
            Map<String, Object> p = policies.get(i);
            String matchLevel = (String) p.get("matchLevel");
            String emoji = switch (matchLevel) {
                case "命中" -> "🟢";
                case "部分匹配" -> "🟡";
                default -> "⚪";
            };
            sb.append(i + 1).append(". ").append(emoji).append(" ")
              .append(p.get("title")).append(" — ").append(matchLevel).append("\n");
            String deadline = (String) p.get("deadlineText");
            if (deadline != null && !deadline.isBlank()) {
                sb.append("   📅 ").append(deadline).append("\n");
            }
        }
        if (policies.size() > preview) {
            sb.append("……其余 ").append(policies.size() - preview)
              .append(" 项已收进下方政策卡片，点「展开其余 N 项」即可查看。\n");
        }
        sb.append("\n💡 点击下方政策卡片可跳转查看详情，或者直接说「帮我申报XX政策」！");

        // 给每个政策加上 navigateTo
        for (Map<String, Object> p : policies) {
            p.put("navigateTo", "/policy/" + p.get("id"));
        }
        Map<String, Object> cardData = buildPolicyListCard(policies, List.of("帮我申报", "搜索政策", "查看我的档案"));
        Long msgId = saveAgentChatLog(userId, convId, AgentAction.MATCH_POLICY, question, sb.toString(), cardData, false, 0, 0);
        return AgentResponse.of(convId, msgId, AgentAction.MATCH_POLICY, sb.toString(), cardData, 0);
    }

    // -------------------- 我的申报 --------------------

    private AgentResponse handleMyApplies(Long convId, Long userId, String question) {
        List<Map<String, Object>> applies = registry.listMyApplies(userId);

        if (applies.isEmpty()) {
            String answer = "您还没有提交过申报。试试说「帮我申报XX补贴」，我帮您一步步完成！";
            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "APPLY_LIST");
            cardData.put("applies", List.of());
            cardData.put("suggestions", List.of("匹配政策", "帮我申报", "搜索知识库"));
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.MY_APPLIES, question, answer, cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.MY_APPLIES, answer, cardData, 0);
        }

        StringBuilder sb = new StringBuilder("📋 您的申报记录：\n\n");
        int idx = 1;
        for (Map<String, Object> a : applies) {
            sb.append(idx++).append(". ").append(a.get("policy_title")).append("\n");
            sb.append("   状态：").append(a.get("statusLabel")).append("\n");
            String createdAt = a.get("created_at") != null ? a.get("created_at").toString().substring(0, 10) : "";
            sb.append("   提交时间：").append(createdAt).append("\n\n");
            // 给每个申报加上 navigateTo
            a.put("navigateTo", "/apply/detail/" + a.get("id"));
        }
        sb.append("💡 点击下方申报记录可查看详情进度。");

        Map<String, Object> cardData = new LinkedHashMap<>();
        cardData.put("type", "APPLY_LIST");
        cardData.put("applies", applies);
        cardData.put("suggestions", List.of("匹配政策", "帮我申报", "搜索政策"));
        Long msgId = saveAgentChatLog(userId, convId, AgentAction.MY_APPLIES, question, sb.toString(), cardData, false, 0, 0);
        return AgentResponse.of(convId, msgId, AgentAction.MY_APPLIES, sb.toString(), cardData, 0);
    }

    // -------------------- 政策详情 --------------------

    private AgentResponse handlePolicyDetail(Long convId, Long userId, String question) {
        // 从匹配列表中找到最相关的政策
        List<Map<String, Object>> policies = registry.matchPolicies(userId);
        String bestTitle = findBestPolicyMatch(question, policies);

        Map<String, Object> detail = null;
        if (bestTitle != null) {
            for (Map<String, Object> p : policies) {
                if (bestTitle.equals(p.get("title")) || p.get("title").toString().contains(bestTitle)) {
                    detail = registry.getPolicyDetail((Long) p.get("id"));
                    break;
                }
            }
        }

        if (detail != null) {
            StringBuilder sb = new StringBuilder("📋 《").append(detail.get("title")).append("》\n\n");
            appendField(sb, "类型", detail.get("category"));
            appendField(sb, "补贴标准", detail.get("subsidy_amount_text"));
            appendField(sb, "截止日期", detail.get("deadline_text"));
            Object conditions = detail.get("conditions");
            if (conditions != null && !"".equals(conditions)) {
                sb.append("📝 申请条件：\n").append(conditions).append("\n\n");
            }
            sb.append("💡 回复「帮我申报").append(detail.get("title")).append("」可立即开始申报。");

            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "POLICY_LIST");
            cardData.put("policies", List.of(detail));
            cardData.put("navigateTo", "/policy/" + detail.get("id"));
            cardData.put("navigateLabel", "查看完整政策 →");
            cardData.put("suggestions", List.of("帮我申报" + detail.get("title"), "匹配其他政策", "查询申报进度"));

            Long msgId = saveAgentChatLog(userId, convId, AgentAction.POLICY_DETAIL, question, sb.toString(), cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.POLICY_DETAIL, sb.toString(), cardData, 0);
        }

        // 没有精确匹配，走知识库搜索
        return handleFreeChat(convId, userId, question);
    }

    // -------------------- 申报状态 --------------------

    private AgentResponse handleApplyStatus(Long convId, Long userId, String question) {
        return handleMyApplies(convId, userId, question);
    }

    // -------------------- 自由对话（AI 兜底） --------------------

    private AgentResponse handleFreeChat(Long convId, Long userId, String question) {
        try {
            ChatResponse cr = ragService.chat(question);
            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "FREE_CHAT");
            cardData.put("suggestions", List.of("我的资料", "匹配政策", "我的申报", "搜索知识库"));
            // 如果来自知识库，带上来源信息
            if (cr.isFromKnowledge() && cr.getSources() != null && !cr.getSources().isEmpty()) {
                cardData.put("sources", cr.getSources());
            }
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.FREE_CHAT,
                    question, cr.getAnswer(), cardData, cr.isFromKnowledge(), 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.FREE_CHAT, cr.getAnswer(), cardData, 0);
        } catch (Exception e) {
            log.error("AI 对话异常: {}", e.getMessage());
            String answer = "抱歉，AI 暂时不可用。您可以试试说「我的资料」或「匹配政策」，这些功能不需要 AI。";
            Map<String, Object> cardData = Map.of("type", "FREE_CHAT",
                    "suggestions", List.of("我的资料", "匹配政策", "我的申报"));
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.FREE_CHAT, question, answer, cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.FREE_CHAT, answer, cardData, 0);
        }
    }

    // ==================== 申报流程 ====================

    private AgentResponse startApply(Long convId, Long userId, String question) {
        // 从消息中提取政策名称关键词
        List<Map<String, Object>> policies = registry.matchPolicies(userId);
        String bestTitle = findBestPolicyMatch(question, policies);

        if (bestTitle == null) {
            // 没指定具体政策 → 列出匹配政策供选择，每条政策带申报跳转链接
            StringBuilder sb = new StringBuilder("请告诉我您想申报哪项政策？\n\n您当前匹配的政策：\n");
            sb.append(listPolicyTitles(policies));
            sb.append("\n💡 点击下方政策卡片的「立即申报」即可跳转到材料填报页。");
            for (Map<String, Object> p : policies) {
                p.put("applyNavigateTo", "/apply/materials?policyId=" + p.get("id"));
            }
            // 2026-10-18：同样走折叠卡片（全量下发 + summary），避免政策多了刷屏
            Map<String, Object> cardData = buildPolicyListCard(policies, List.of("匹配政策", "搜索政策"));
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.START_APPLY, question, sb.toString(), cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.START_APPLY, sb.toString(), cardData, 0);
        }

        // 找到政策ID
        Long policyId = null;
        String policyTitle = bestTitle;
        for (Map<String, Object> p : policies) {
            if (bestTitle.equals(p.get("title")) || bestTitle.contains((String) p.get("title"))) {
                policyId = (Long) p.get("id");
                policyTitle = (String) p.get("title");
                break;
            }
        }
        if (policyId == null) {
            // 查DB
            Map<String, Object> detail = registry.getPolicyDetail(policies.isEmpty() ? null :
                    (Long) policies.get(0).get("id"));
            if (detail != null) {
                policyId = (Long) detail.get("id");
                policyTitle = (String) detail.get("title");
            }
        }

        if (policyId == null) {
            String answer = "没找到这个政策，请确认政策名称或先说「匹配政策」看看有哪些适合您。";
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.START_APPLY, question, answer, null, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.START_APPLY, answer, null, 0);
        }

        // 跳转到材料填报页面，不再在对话中逐项填写
        String navigateTo = "/apply/materials?policyId=" + policyId;
        String answer = "好的，正在为您跳转到《" + policyTitle + "》的申报材料页，请逐项填写并上传所需材料后提交。";

        Map<String, Object> cardData = new LinkedHashMap<>();
        cardData.put("type", "APPLY_FORM");
        cardData.put("policyTitle", policyTitle);
        cardData.put("policyId", policyId);
        cardData.put("navigateTo", navigateTo);
        cardData.put("navigateLabel", "前往填写申报材料 →");
        cardData.put("autoNavigate", true);

        Long msgId = saveAgentChatLog(userId, convId, AgentAction.START_APPLY, question, answer, cardData, false, 0, 0);
        return AgentResponse.of(convId, msgId, AgentAction.START_APPLY, answer, cardData, 0);
    }

    private AgentResponse handleApplyDraft(Long convId, Long userId, ApplyDraft draft, String question) {
        AgentAction action = intentDetect(question, true);

        if (action == AgentAction.MODIFY_FIELD) {
            return handleModifyField(convId, userId, draft, question);
        }

        if (action == AgentAction.SUBMIT_APPLY) {
            return submitApply(convId, userId, draft);
        }

        if (action == AgentAction.CANCEL_ACTION) {
            drafts.remove(convId);
            clearDraft(convId);
            String answer = "已取消当前申报流程。有什么其他需要帮您的吗？";
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.CANCEL_ACTION, question, answer, null, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.CANCEL_ACTION, answer, null, 0);
        }

        // FILL_FIELD：填充字段
        if (!draft.missingFields.isEmpty()) {
            String key = draft.missingFields.remove(0);
            draft.filledFields.put(key, question); // 用户的回复就是字段值
            persistDraft(convId, draft);

            if (draft.missingFields.isEmpty()) {
                return confirmApply(convId, userId, draft, question);
            }

            // 还有缺失字段，继续问
            String nextKey = draft.missingFields.get(0);
            StringBuilder answer = new StringBuilder("已记录！");
            if (draft.missingFields.size() > 1) {
                answer.append("还需提供 ").append(draft.missingFields.size()).append(" 项：");
                for (String k : draft.missingFields) {
                    answer.append("\n  · ").append(k);
                    if (isImageField(k)) answer.append(" 🖼️");
                }
                answer.append("\n\n请提供「").append(nextKey).append("」");
                if (isImageField(nextKey)) answer.append("（请上传后发送路径）");
                answer.append("：");
            } else {
                if (isImageField(nextKey)) {
                    answer.append("最后一项「").append(nextKey).append("」需要上传，请上传后发送路径：");
                } else {
                    answer.append("最后一项「").append(nextKey).append("」：");
                }
            }
            Map<String, Object> cardData = buildApplyCard(draft);

            Long msgId = saveAgentChatLog(userId, convId, AgentAction.FILL_FIELD, question, answer.toString(), cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.FILL_FIELD, answer.toString(), cardData, 0);
        }

        return confirmApply(convId, userId, draft, question);
    }

    /**
     * 处理用户修改预填字段的请求，如"修改plantType为水稻种植"。
     */
    private AgentResponse handleModifyField(Long convId, Long userId, ApplyDraft draft, String question) {
        String m = question.strip();
        String foundKey = null;
        String newValue = null;

        // 在已填字段中匹配 key
        for (String key : draft.filledFields.keySet()) {
            if (m.contains(key)) {
                foundKey = key;
                break;
            }
        }

        if (foundKey != null) {
            String[] delimiters = {"为", "改成", "改为", "应该是", "是"};
            for (String delim : delimiters) {
                int idx = m.indexOf(delim);
                if (idx >= 0) {
                    String candidate = m.substring(idx + delim.length()).strip();
                    if (!candidate.isEmpty() && !candidate.equals(foundKey)) {
                        newValue = candidate;
                        break;
                    }
                }
            }
        }

        if (foundKey != null && newValue != null) {
            draft.filledFields.put(foundKey, newValue);
            persistDraft(convId, draft);

            StringBuilder answer = new StringBuilder("已修改「").append(foundKey).append("」为：").append(newValue).append("\n\n");
            if (!draft.missingFields.isEmpty()) {
                String nextKey = draft.missingFields.get(0);
                answer.append("还需要提供「").append(nextKey).append("」，或者直接回复「确认提交」提交申报。");
            } else {
                answer.append("所有材料已齐全！回复「确认提交」即可提交申报。");
            }

            Map<String, Object> applyCard = buildApplyCard(draft);
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.MODIFY_FIELD, question, answer.toString(), applyCard, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.MODIFY_FIELD, answer.toString(), applyCard, 0);
        }

        // 解析失败，引导用户
        StringBuilder answer = new StringBuilder("请说明要修改哪个字段，比如「修改plantType为水稻种植」。\n\n当前已填：\n");
        for (Map.Entry<String, Object> e : draft.filledFields.entrySet()) {
            answer.append("  · ").append(e.getKey()).append("：").append(formatValue(e.getValue())).append("\n");
        }
        Map<String, Object> fallbackCard = buildApplyCard(draft);
        Long msgId = saveAgentChatLog(userId, convId, AgentAction.MODIFY_FIELD, question, answer.toString(), fallbackCard, false, 0, 0);
        return AgentResponse.of(convId, msgId, AgentAction.MODIFY_FIELD, answer.toString(), fallbackCard, 0);
    }

    private Map<String, Object> buildApplyCard(ApplyDraft draft) {
        Map<String, Object> cardFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : draft.filledFields.entrySet()) {
            if (e.getValue() != null && !"".equals(e.getValue())) {
                cardFields.put(e.getKey(), e.getValue());
            }
        }
        return Map.of("type", "APPLY_FORM",
                "policyTitle", draft.policyTitle,
                "filledFields", cardFields,
                "missingFields", draft.missingFields,
                "readyToSubmit", draft.missingFields.isEmpty());
    }

    private AgentResponse submitApply(Long convId, Long userId, ApplyDraft draft) {
        if (draft.submitted) {
            String answer = "该申报已提交成功，无需重复提交。回复「我的申报」查看进度。";
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.SUBMIT_APPLY, "确认提交", answer, null, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.SUBMIT_APPLY, answer, null, 0);
        }

        try {
            // 构建 material_data JSON（仅含非空字段）
            Map<String, Object> materialMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : draft.filledFields.entrySet()) {
                if (e.getValue() != null && !"".equals(e.getValue())) {
                    materialMap.put(e.getKey(), e.getValue());
                }
            }
            String materialData = objectMapper.writeValueAsString(materialMap);

            // 获取 village_code / town_code
            Map<String, String> codes = registry.getUserRegionCodes(userId);
            String villageCode = codes.getOrDefault("villageCode", "");
            String townCode = codes.getOrDefault("townCode", "");

            // 写入 subsidy_apply 表
            Long applyId = registry.insertApply(userId, draft.policyId, materialData, villageCode, townCode);
            drafts.remove(convId);
            clearDraft(convId);

            StringBuilder sb = new StringBuilder("🎉 申报提交成功！\n\n");
            sb.append("政策：").append(draft.policyTitle).append("\n");
            sb.append("申报编号：").append(applyId).append("\n");
            sb.append("状态：待审核\n\n");
            sb.append("回复「我的申报」可随时查看进度。");

            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "APPLY_FORM");
            cardData.put("policyTitle", draft.policyTitle);
            cardData.put("filledFields", materialMap);
            cardData.put("submitted", true);
            cardData.put("applyId", applyId);
            cardData.put("navigateTo", "/apply/detail/" + applyId);
            cardData.put("suggestions", List.of("我的申报", "匹配政策", "我要申报别的"));

            Long msgId = saveAgentChatLog(userId, convId, AgentAction.SUBMIT_APPLY, "确认提交", sb.toString(), cardData, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.SUBMIT_APPLY, sb.toString(), cardData, 0);

        } catch (Exception e) {
            log.error("提交申报失败: {}", e.getMessage());
            String answer = "提交失败，请稍后再试。错误：" + e.getMessage();
            Long msgId = saveAgentChatLog(userId, convId, AgentAction.SUBMIT_APPLY, "确认提交", answer, null, false, 0, 0);
            return AgentResponse.of(convId, msgId, AgentAction.SUBMIT_APPLY, answer, null, 0);
        }
    }

    private AgentResponse confirmApply(Long convId, Long userId, ApplyDraft draft, String question) {
        StringBuilder sb = new StringBuilder("📋 申报确认\n\n");
        sb.append("政策：《").append(draft.policyTitle).append("》\n\n");
        sb.append("将提交以下材料：\n");
        for (Map.Entry<String, Object> e : draft.filledFields.entrySet()) {
            if (e.getValue() != null && !"".equals(e.getValue())) {
                sb.append("  · ").append(e.getKey()).append("：").append(formatValue(e.getValue())).append("\n");
            }
        }
        sb.append("\n回复「确认提交」提交申报\n");
        sb.append("回复「修改XX为YY」可调整信息\n");
        sb.append("回复「取消」放弃本次申报");

        Map<String, Object> cardData = buildApplyCard(draft);

        Long msgId = saveAgentChatLog(userId, convId, AgentAction.FILL_FIELD, question, sb.toString(), cardData, false, 0, 0);
        return AgentResponse.of(convId, msgId, AgentAction.FILL_FIELD, sb.toString(), cardData, 0);
    }

    // ==================== SSE 流式 AI 对话 ====================

    private void streamFreeChat(Long convId, Long userId, String question, AgentAction action, SseEmitter emitter) {
        try {
            sendSseEvent(emitter, "action", mapOf("agentAction", action.name(), "message", "正在思考..."));

            ChatResponse cr = ragService.chat(question);
            String answer = cr.getAnswer();

            sendSseEvent(emitter, "token", answer);

            Map<String, Object> cardMap = new LinkedHashMap<>();
            cardMap.put("type", "FREE_CHAT");
            if (cr.isFromKnowledge() && cr.getSources() != null && !cr.getSources().isEmpty()) {
                cardMap.put("sources", cr.getSources());
            }
            sendSseEvent(emitter, "card", cardMap);

            // 知识库搜索后，提供更丰富的快捷操作
            if (action == AgentAction.SEARCH_KB) {
                sendSseEvent(emitter, "quick", mapOf("suggestions",
                        List.of("匹配适合我的政策", "帮我申报", "我的申报进度"),
                        "label", "试试这些："));
            } else {
                sendSseEvent(emitter, "quick", mapOf("suggestions",
                        List.of("我的资料", "匹配政策", "我的申报", "搜索知识库"),
                        "label", "还可以问我："));
            }

            Long msgId = saveAgentChatLog(userId, convId, action, question, answer,
                    cardMap, cr.isFromKnowledge(), 0, 0);
            sendSseEvent(emitter, "done", mapOf("conversationId", convId, "messageId", msgId));

        } catch (Exception e) {
            log.error("流式AI对话异常: {}", e.getMessage());
            sendSseEvent(emitter, "error", mapOf("message", "AI 暂时不可用，请稍后再试"));
        }
    }

    // ==================== 日志记录 ====================

    private Long saveAgentChatLog(Long userId, Long conversationId, AgentAction action,
                                  String question, String answer, Object cardData,
                                  boolean fromKnowledge, int promptTokens, int completionTokens) {
        try {
            AiChatLog log = new AiChatLog();
            log.setUserId(userId);
            log.setConversationId(conversationId);
            log.setAgentAction(action.name());

            UserContext.LoginUser user = UserContext.get();
            log.setUsername(user != null && user.getName() != null ? user.getName() : "村民");
            log.setPhone(user != null && user.getPhone() != null ? user.getPhone() : "");
            log.setQuestion(question);
            log.setAnswer(answer != null && answer.length() > 2000 ? answer.substring(0, 2000) : answer);
            if (cardData != null) {
                try { log.setCardData(objectMapper.writeValueAsString(cardData)); } catch (Exception ignored) {}
            }
            log.setFromKnowledge(fromKnowledge ? 1 : 0);
            log.setModel(aiConfigMapper.findFirst() != null ? aiConfigMapper.findFirst().getModelName() : "deepseek-ai/DeepSeek-V3");
            log.setPromptTokens(promptTokens);
            log.setCompletionTokens(completionTokens);
            chatLogMapper.insert(log);

            // 更新会话信息
            convMapper.incrementMsgCount(conversationId);
            convMapper.updateLastAction(conversationId, action.name());

            // 自动标题（取首条非闲聊消息前15字）
            updateTitleIfNeeded(conversationId, question);

            return log.getId();
        } catch (Exception e) {
            log.warn("记录 Agent 对话日志失败: {}", e.getMessage());
            return null;
        }
    }

    private void updateTitleIfNeeded(Long conversationId, String question) {
        try {
            AiConversation conv = convMapper.selectByIdAndUserId(conversationId, null);
            // 绕过 userId 校验查标题
            String sql = "SELECT title FROM ai_conversation WHERE id = ?";
            String title = jdbc.queryForObject(sql, String.class, conversationId);
            if ("新对话".equals(title) && question != null && question.length() > 1) {
                String newTitle = question.length() > 15 ? question.substring(0, 15) : question;
                convMapper.updateTitle(conversationId, newTitle);
            }
        } catch (Exception ignored) {
            // 自动标题失败不影响主流程
        }
    }

    // ==================== 工具方法 ====================

    private Long ensureConversation(Long convId, Long userId) {
        if (convId != null) {
            AiConversation conv = convMapper.selectByIdAndUserId(convId, userId);
            if (conv != null) {
                return convId;
            }
        }
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");
        convMapper.insert(conv);
        return conv.getId();
    }

    private String buildGreeting(String name) {
        int hour = LocalTime.now().getHour();
        String prefix;
        if (hour < 6) prefix = "夜深了，";
        else if (hour < 12) prefix = "上午好，";
        else if (hour < 14) prefix = "中午好，";
        else if (hour < 18) prefix = "下午好，";
        else prefix = "晚上好，";
        return prefix + name + "！";
    }

    private String[] buildSuggestions(WelcomeVO.Summary s) {
        List<String> list = new ArrayList<>();
        if (s.getMatchedPolicies() > 0) {
            list.add("帮我看看能领哪些补贴");
        }
        if (s.getProfileCompleteness() < 60) {
            list.add("帮我完善个人资料");
        }
        if (s.getPendingApplies() > 0) {
            list.add("我上次申报到哪步了");
        }
        list.add("介绍一下怎么申请补贴");
        list.add("搜索最新惠农政策");
        return list.toArray(new String[0]);
    }

    private boolean matchAny(String message, String... keywords) {
        for (String kw : keywords) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 检测无草稿时用户是否在尝试提交（"确认提交"等关键词）。
     * 防止草稿丢失后用户说"确认提交"被当成自由对话发给 AI。
     */
    private boolean isSubmitWithoutDraft(AgentAction action, ApplyDraft draft, String question) {
        if (draft != null) return false;
        if (action == AgentAction.SUBMIT_APPLY) return true;
        // intentDetect 在无草稿上下文不会返回 SUBMIT_APPLY，这里做兜底检测
        String m = question.toLowerCase().strip();
        return matchAny(m, "确认提交", "确认申报", "提交申请");
    }

    /**
     * 将申报草稿持久化到数据库（服务器重启不丢失）。
     */
    private void persistDraft(Long convId, ApplyDraft draft) {
        try {
            String json = objectMapper.writeValueAsString(draft);
            convMapper.saveDraftData(convId, json);
        } catch (Exception e) {
            log.warn("草稿持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 从数据库恢复申报草稿。
     */
    private ApplyDraft restoreDraft(Long convId) {
        try {
            String json = convMapper.selectDraftData(convId);
            if (json != null && !json.isBlank()) {
                ApplyDraft draft = objectMapper.readValue(json, ApplyDraft.class);
                drafts.put(convId, draft);
                log.info("从数据库恢复草稿: convId={}, policyTitle={}", convId, draft.policyTitle);
                return draft;
            }
        } catch (Exception e) {
            log.warn("草稿恢复失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 清除数据库中的草稿数据。
     */
    private void clearDraft(Long convId) {
        try {
            convMapper.clearDraftData(convId);
        } catch (Exception e) {
            log.warn("清除草稿失败: {}", e.getMessage());
        }
    }

    private String findBestPolicyMatch(String question, List<Map<String, Object>> policies) {
        String best = null;
        int bestLen = 0;
        for (Map<String, Object> p : policies) {
            String title = (String) p.get("title");
            if (title != null && question.contains(title)) {
                if (title.length() > bestLen) {
                    best = title;
                    bestLen = title.length();
                }
            }
        }
        return best;
    }

    private String listPolicyTitles(List<Map<String, Object>> policies) {
        if (policies.isEmpty()) return "暂无匹配政策";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(policies.size(), 5); i++) {
            sb.append("  · ").append(policies.get(i).get("title")).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseMaterialSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 判断字段是否为图片/文件上传类型（这些字段不应自动预填）。
     */
    private boolean isImageField(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("id") && (lower.contains("front") || lower.contains("back") || lower.contains("photo"))
                || lower.contains("photourl") || lower.contains("image") || lower.contains("img")
                || lower.contains("file") || lower.contains("upload")
                || lower.contains("附件") || lower.contains("照片") || lower.contains("证明")
                || lower.contains("证书") || lower.contains("扫描");
    }

    private String getFieldLabel(List<Map<String, String>> schema, String key) {
        for (Map<String, String> f : schema) {
            if (key.equals(f.get("key"))) {
                String label = f.get("label");
                return label != null ? label : key;
            }
        }
        return key;
    }

    private String getFieldUnit(List<Map<String, String>> schema, String key) {
        for (Map<String, String> f : schema) {
            if (key.equals(f.get("key"))) return f.get("unit");
        }
        return null;
    }

    private String actionLabel(AgentAction action) {
        return switch (action) {
            case VIEW_PROFILE -> "查看您的资料";
            case MATCH_POLICY -> "匹配适合的政策";
            case START_APPLY -> "准备申报材料";
            case MY_APPLIES -> "查询申报记录";
            case SEARCH_KB -> "搜索知识库";
            default -> "思考中";
        };
    }

    private void appendField(StringBuilder sb, String label, Object value, String unit) {
        if (value != null && !"".equals(value) && !"null".equals(String.valueOf(value))) {
            sb.append("· ").append(label).append("：").append(value);
            if (unit != null && !unit.isEmpty()) sb.append(unit);
            sb.append("\n");
        }
    }

    private void appendField(StringBuilder sb, String label, Object value) {
        appendField(sb, label, value, null);
    }

    private String formatValue(Object v) {
        if (v == null) return "";
        String s = v.toString();
        // 银行卡脱敏
        if (s.length() > 12 && s.matches("\\d+")) {
            return "****" + s.substring(s.length() - 4);
        }
        // 面积加单位（如果包含小数点）
        return s;
    }

    private Map<String, Object> mapOf(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    private void sendSseEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 根据动作类型发送快捷建议事件。
     */
    private void sendQuickEvent(SseEmitter emitter, AgentAction action, AgentResponse resp) {
        try {
            String navigateTo = null;
            List<String> suggestions;
            switch (action) {
                case VIEW_PROFILE -> {
                    suggestions = List.of("匹配适合我的政策", "帮我申报", "搜索知识库");
                    navigateTo = "/mine";
                }
                case MATCH_POLICY -> {
                    suggestions = List.of("帮我申报", "搜索政策详情", "查看我的档案");
                }
                case MY_APPLIES -> {
                    suggestions = List.of("匹配政策", "帮我申报", "搜索知识库");
                }
                case POLICY_DETAIL -> {
                    suggestions = List.of("帮我申报", "匹配其他政策", "我的申报");
                    // navigateTo from card data
                    if (resp.getData() != null && resp.getData().containsKey("navigateTo")) {
                        navigateTo = (String) resp.getData().get("navigateTo");
                    }
                }
                default -> {
                    suggestions = List.of("我的资料", "匹配政策", "我的申报");
                }
            }
            Map<String, Object> quickData = new LinkedHashMap<>();
            quickData.put("suggestions", suggestions);
            quickData.put("label", "还可以：");
            if (navigateTo != null) {
                quickData.put("navigateTo", navigateTo);
                quickData.put("navigateLabel", "点击跳转 →");
            }
            emitter.send(SseEmitter.event().name("quick").data(objectMapper.writeValueAsString(quickData)));
        } catch (Exception e) {
            log.debug("发送 quick 事件失败: {}", e.getMessage());
        }
    }

    // ==================== 内部类 ====================

    static class ApplyDraft {
        Long policyId;
        String policyTitle;
        Map<String, Object> filledFields;
        List<String> missingFields;
        boolean submitted;

        ApplyDraft() {}

        ApplyDraft(Long policyId, String policyTitle, Map<String, Object> filledFields, List<String> missingFields) {
            this.policyId = policyId;
            this.policyTitle = policyTitle;
            this.filledFields = filledFields;
            this.missingFields = missingFields;
            this.submitted = false;
        }

        public Long getPolicyId() { return policyId; }
        public void setPolicyId(Long policyId) { this.policyId = policyId; }
        public String getPolicyTitle() { return policyTitle; }
        public void setPolicyTitle(String policyTitle) { this.policyTitle = policyTitle; }
        public Map<String, Object> getFilledFields() { return filledFields; }
        public void setFilledFields(Map<String, Object> filledFields) { this.filledFields = filledFields; }
        public List<String> getMissingFields() { return missingFields; }
        public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
        public boolean isSubmitted() { return submitted; }
        public void setSubmitted(boolean submitted) { this.submitted = submitted; }
    }
}