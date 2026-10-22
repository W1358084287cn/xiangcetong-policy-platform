package com.xiangcetong.config;

import com.xiangcetong.ai.service.KnowledgeService;
import com.xiangcetong.common.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动时自愈：如果向量知识库为空，自动从 subsidy_policy 全量重建。
 *
 * 背景（2026-10-18 线上重大 BUG）：
 *   容器化迁移后 knowledge_document / knowledge_chunk 两张表是空的，
 *   而 subsidy_policy 里有 62 条政策。RagService 检测到分块为空后走「直接对话」分支，
 *   既没有政策上下文、联网搜索又超时，大模型只能反问"您想了解哪方面的惠农政策"，
 *   表现为严重的答非所问（问义务兵政策、答"请问您想了解哪方面"）。
 *
 * 设计要点：
 *   1. 只在「没有任何有效向量分块」时才触发，正常启动不会重复调用 embedding 接口；
 *   2. 放在独立守护线程里执行并先等几秒，避免拖慢应用启动、影响容器健康检查；
 *   3. 任何异常只记日志，绝不影响主流程启动；
 *   4. 通过写入系统身份绕过 importPoliciesFromDb 的 SYS_ADMIN 校验。
 */
@Component
@Order(100)
public class KnowledgeBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrapRunner.class);

    /** 启动后等待时间：让建表类 Runner 先跑完 */
    private static final long STARTUP_DELAY_MS = 8000L;

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeService knowledgeService;

    public KnowledgeBootstrapRunner(JdbcTemplate jdbcTemplate, KnowledgeService knowledgeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(String... args) {
        Thread t = new Thread(this::bootstrap, "kb-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private void bootstrap() {
        try {
            Thread.sleep(STARTUP_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            Integer validChunks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_chunk WHERE embedding IS NOT NULL AND embedding <> ''",
                    Integer.class);
            if (validChunks != null && validChunks > 0) {
                log.info("知识库自检通过：已有 {} 个向量分块，跳过自动重建", validChunks);
                return;
            }

            Integer policies = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM subsidy_policy", Integer.class);
            if (policies == null || policies == 0) {
                log.warn("知识库为空且 subsidy_policy 无数据，跳过自动重建（请先在管理端录入政策）");
                return;
            }

            log.warn("检测到向量知识库为空（政策表 {} 条），开始自动重建，避免 AI 答非所问...", policies);

            // 以系统身份执行，绕过 SYS_ADMIN 校验
            UserContext.LoginUser system = new UserContext.LoginUser();
            system.setUserId(1L);
            system.setName("系统");
            system.setRole("SYS_ADMIN");
            UserContext.set(system);
            try {
                Map<String, Object> result = knowledgeService.importPoliciesFromDb();
                log.info("向量知识库自动重建完成: {}", result);
            } finally {
                UserContext.clear();
            }
        } catch (Exception e) {
            // 自愈失败不影响主流程；管理端仍可手动点「导入政策到知识库」
            log.error("向量知识库自动重建失败（不影响服务启动，可在管理端手动导入）: {}", e.getMessage());
        }
    }
}
