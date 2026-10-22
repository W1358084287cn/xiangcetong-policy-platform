package com.xiangcetong.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时自动补全种子政策的 material_schema 材料清单。
 * patch-seed-60-policies.sql 的 INSERT 不含 material_schema 列，导致前端显示"共 0 项材料"。
 * 根据政策类别（GRAIN/MACHINERY/LIVESTOCK/AQUACULTURE/SOCIAL/GENERAL）自动生成对应材料 Schema。
 */
@Component
public class MaterialSchemaFixRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MaterialSchemaFixRunner.class);

    // ==================== 公共材料块（不可变） ====================

    private static final String ID_CARD_BLOCK = """
        {"code":"id_card","title":"身份证明","desc":"请上传身份证正反面","fields":[\
          {"key":"id_front","type":"photo","required":true,"label":"身份证正面"},\
          {"key":"id_back","type":"photo","required":true,"label":"身份证反面"},\
          {"key":"name","type":"text","required":true,"label":"姓名"},\
          {"key":"id_number","type":"text","required":true,"label":"身份证号"}\
        ]}""";

    private static final String BANK_CARD_BLOCK = """
        {"code":"bank_card","title":"银行卡信息","desc":"用于接收补贴款项","fields":[\
          {"key":"bank_card_number","type":"text","required":true,"label":"银行卡号"},\
          {"key":"bank_name","type":"text","required":true,"label":"开户行名称"}\
        ]}""";

    private static final String LAND_PROOF_BLOCK = """
        {"code":"land_proof","title":"土地证明","desc":"土地承包合同或村委会出具的种植面积证明","fields":[\
          {"key":"land_contract","type":"photo","required":true,"label":"土地承包合同或证明"},\
          {"key":"plant_area","type":"number","required":true,"label":"种植面积（亩）"}\
        ]}""";

    private static final String MACHINE_INVOICE_BLOCK = """
        {"code":"machine_invoice","title":"购机证明","desc":"购机发票和机具合格证","fields":[\
          {"key":"invoice","type":"photo","required":true,"label":"购机发票"},\
          {"key":"certificate","type":"photo","required":true,"label":"机具合格证"}\
        ]}""";

    private static final String MACHINE_INFO_BLOCK = """
        {"code":"machine_info","title":"机具信息","desc":"填写所购机具的基本信息","fields":[\
          {"key":"machine_name","type":"text","required":true,"label":"机具名称"},\
          {"key":"machine_model","type":"text","required":true,"label":"机具型号"},\
          {"key":"purchase_date","type":"date","required":true,"label":"购买日期"}\
        ]}""";

    private static final String FARM_PROOF_BLOCK = """
        {"code":"farm_proof","title":"养殖证明","desc":"养殖场营业执照或村委会出具的养殖规模证明","fields":[\
          {"key":"farm_license","type":"photo","required":true,"label":"养殖场营业执照或证明"},\
          {"key":"livestock_count","type":"number","required":true,"label":"存栏数量"}\
        ]}""";

    private static final String AQUACULTURE_PROOF_BLOCK = """
        {"code":"aqua_proof","title":"水产养殖证明","desc":"水域滩涂养殖证或承包合同","fields":[\
          {"key":"aqua_license","type":"photo","required":true,"label":"养殖证或承包合同"},\
          {"key":"aqua_area","type":"number","required":true,"label":"养殖面积（亩）"}\
        ]}""";

    private static final String ELIGIBILITY_PROOF_BLOCK = """
        {"code":"eligibility_proof","title":"资格证明","desc":"请上传相关资格或认证证明文件","fields":[\
          {"key":"cert_file","type":"photo","required":true,"label":"资格/认证证明文件"},\
          {"key":"cert_number","type":"text","required":false,"label":"证书编号"}\
        ]}""";

    // ==================== 分类 Schema 模板 ====================

    /** 粮食种植类：身份证明 + 土地证明 + 银行卡 */
    private static final String GRAIN_SCHEMA = buildSchema(ID_CARD_BLOCK, LAND_PROOF_BLOCK, BANK_CARD_BLOCK);

    /** 农机装备类：身份证明 + 购机证明 + 机具信息 + 银行卡 */
    private static final String MACHINERY_SCHEMA = buildSchema(ID_CARD_BLOCK, MACHINE_INVOICE_BLOCK, MACHINE_INFO_BLOCK, BANK_CARD_BLOCK);

    /** 畜牧养殖类：身份证明 + 养殖证明 + 银行卡 */
    private static final String LIVESTOCK_SCHEMA = buildSchema(ID_CARD_BLOCK, FARM_PROOF_BLOCK, BANK_CARD_BLOCK);

    /** 水产养殖类：身份证明 + 水产养殖证明 + 银行卡 */
    private static final String AQUACULTURE_SCHEMA = buildSchema(ID_CARD_BLOCK, AQUACULTURE_PROOF_BLOCK, BANK_CARD_BLOCK);

    /** 社保优抚类：身份证明 + 资格证明 + 银行卡 */
    private static final String SOCIAL_SCHEMA = buildSchema(ID_CARD_BLOCK, ELIGIBILITY_PROOF_BLOCK, BANK_CARD_BLOCK);

    /** 综合/其他：身份证明 + 资格证明 + 银行卡 */
    private static final String GENERAL_SCHEMA = buildSchema(ID_CARD_BLOCK, ELIGIBILITY_PROOF_BLOCK, BANK_CARD_BLOCK);

    private final JdbcTemplate jdbcTemplate;

    public MaterialSchemaFixRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("开始检查并补全 material_schema...");
        try {
            // 查询所有 material_schema 缺失（NULL、空字符串、空对象 {}）的政策
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, category FROM subsidy_policy " +
                "WHERE material_schema IS NULL " +
                "   OR TRIM(material_schema) = '' " +
                "   OR TRIM(material_schema) = '{}' " +
                "   OR TRIM(material_schema) = 'null'"
            );

            if (rows.isEmpty()) {
                log.info("所有政策均已配置有效的 material_schema，无需补全");
                return;
            }

            log.info("发现 {} 条政策 material_schema 缺失，开始补全...", rows.size());
            int updated = 0;
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                String title = (String) row.get("title");
                String category = (String) row.get("category");

                String schema = getSchemaByCategory(category);
                if (schema == null) {
                    log.warn("政策 [id={}, title={}] 类别 {} 无对应 Schema，跳过", id, title, category);
                    continue;
                }

                jdbcTemplate.update(
                    "UPDATE subsidy_policy SET material_schema = ? WHERE id = ?",
                    schema, id
                );
                log.info("已补全 policy id={} title=\"{}\" category={}", id, title, category);
                updated++;
            }

            log.info("MaterialSchemaFixRunner 完成：已为 {} 条政策补全 material_schema", updated);
        } catch (Exception e) {
            log.error("补全 material_schema 失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 根据政策类别返回对应的材料 Schema JSON
     */
    private String getSchemaByCategory(String category) {
        if (category == null) return GENERAL_SCHEMA;
        return switch (category.toUpperCase()) {
            case "GRAIN" -> GRAIN_SCHEMA;
            case "MACHINERY" -> MACHINERY_SCHEMA;
            case "LIVESTOCK" -> LIVESTOCK_SCHEMA;
            case "AQUACULTURE" -> AQUACULTURE_SCHEMA;
            case "SOCIAL" -> SOCIAL_SCHEMA;
            case "GENERAL", "OTHER" -> GENERAL_SCHEMA;
            default -> GENERAL_SCHEMA;
        };
    }

    /**
     * 将多个材料块组装为完整 Schema JSON
     */
    private static String buildSchema(String... blocks) {
        StringBuilder sb = new StringBuilder("{\"materials\":[");
        for (int i = 0; i < blocks.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(blocks[i]);
        }
        sb.append("]}");
        return sb.toString();
    }
}