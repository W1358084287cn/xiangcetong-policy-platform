package com.xiangcetong.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MaterialSchemaFixRunner 单元测试：启动时自动补全缺失 material_schema 的政策。
 */
class MaterialSchemaFixRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private MaterialSchemaFixRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        runner = new MaterialSchemaFixRunner(jdbcTemplate);
    }

    @Test
    void noPoliciesNeedFix_doesNotUpdate() throws Exception {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());

        runner.run();

        verify(jdbcTemplate, times(0)).update(anyString(), any(), any());
    }

    @Test
    void grainPolicyMissingSchema_generatesAndUpdates() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 1L, "title", "种粮补贴", "category", "GRAIN"));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(1L));
        String schema = schemaCaptor.getValue();
        assertNotNull(schema);
        assertTrue(schema.contains("id_card"), "GRAIN Schema 应包含身份证明");
        assertTrue(schema.contains("land_proof"), "GRAIN Schema 应包含土地证明");
        assertTrue(schema.contains("bank_card"), "GRAIN Schema 应包含银行卡信息");
    }

    @Test
    void socialPolicyMissingSchema_generatesEligibilityProof() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 332L, "title", "义务兵家庭优待金", "category", "SOCIAL"));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(332L));
        String schema = schemaCaptor.getValue();
        assertTrue(schema.contains("eligibility_proof"), "SOCIAL Schema 应包含资格证明");
    }

    @Test
    void unknownCategoryFallsBackToGeneral() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 99L, "title", "未知类别政策", "category", "UNKNOWN_CAT"));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(99L));
        String schema = schemaCaptor.getValue();
        assertTrue(schema.contains("eligibility_proof"), "未知类别应使用 GENERAL Schema（含资格证明）");
    }

    @Test
    void nullCategoryFallsBackToGeneral() throws Exception {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 100L);
        row.put("title", "无类别政策");
        row.put("category", null);
        List<Map<String, Object>> rows = List.of(row);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(100L));
        assertTrue(schemaCaptor.getValue().contains("id_card"), "null 类别应使用 GENERAL Schema");
    }

    @Test
    void exceptionDoesNotCrash() throws Exception {
        when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("DB 挂了"));

        runner.run();
        // 吞掉异常不崩启动
    }

    @Test
    void machinerySchemaContainsMachineBlocks() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 2L, "title", "农机补贴", "category", "MACHINERY"));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(2L));
        String schema = schemaCaptor.getValue();
        assertTrue(schema.contains("machine_invoice"), "MACHINERY Schema 应包含购机证明");
        assertTrue(schema.contains("machine_info"), "MACHINERY Schema 应包含机具信息");
    }

    @Test
    void livestockSchemaContainsFarmProof() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 3L, "title", "畜牧养殖补贴", "category", "LIVESTOCK"));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(3L));
        assertTrue(schemaCaptor.getValue().contains("farm_proof"), "LIVESTOCK Schema 应包含养殖证明");
    }

    @Test
    void aquacultureSchemaContainsAquaProof() throws Exception {
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", 4L, "title", "水产养殖补贴", "category", "AQUACULTURE"));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        runner.run();

        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), schemaCaptor.capture(), eq(4L));
        assertTrue(schemaCaptor.getValue().contains("aqua_proof"), "AQUACULTURE Schema 应包含水产养殖证明");
    }
}