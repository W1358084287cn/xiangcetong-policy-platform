package com.xiangcetong.policy.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 种养/主体归一单元测试（答辩演示常用中文输入）。
 */
class PolicyMatchNormalizeTest {

    @Test
    void plant_wheat_mapsToRice() {
        assertEquals("RICE", PolicyMatchNormalize.normalizePlant("小麦"));
        assertEquals("RICE", PolicyMatchNormalize.normalizePlant("玉米"));
        assertEquals("RICE", PolicyMatchNormalize.normalizePlant("大豆"));
    }

    @Test
    void plant_livestock_keywords() {
        assertEquals("LIVESTOCK", PolicyMatchNormalize.normalizePlant("畜禽养殖"));
        assertEquals("LIVESTOCK", PolicyMatchNormalize.normalizePlant("生猪"));
    }

    @Test
    void plant_aquaculture_keywords() {
        assertEquals("AQUACULTURE", PolicyMatchNormalize.normalizePlant("水产养殖"));
        assertEquals("AQUACULTURE", PolicyMatchNormalize.normalizePlant("养鱼"));
    }

    @Test
    void plant_enum_passthrough() {
        assertEquals("RICE", PolicyMatchNormalize.normalizePlant("rice"));
        assertEquals("LIVESTOCK", PolicyMatchNormalize.normalizePlant("LIVESTOCK"));
    }

    @Test
    void plant_null_empty() {
        assertEquals("", PolicyMatchNormalize.normalizePlant(null));
        assertEquals("OTHER", PolicyMatchNormalize.normalizePlant("未知作物X"));
    }

    @Test
    void subject_chinese_and_enum() {
        assertEquals("FAMILY_FARM", PolicyMatchNormalize.normalizeSubject("家庭农场"));
        assertEquals("FARMER", PolicyMatchNormalize.normalizeSubject("普通农户"));
        assertEquals("COOP", PolicyMatchNormalize.normalizeSubject("农民专业合作社"));
        assertEquals("ENTERPRISE", PolicyMatchNormalize.normalizeSubject("农业企业"));
        assertEquals("FAMILY_FARM", PolicyMatchNormalize.normalizeSubject("FAMILY_FARM"));
    }

    @Test
    void subject_blank() {
        assertEquals("", PolicyMatchNormalize.normalizeSubject(null));
        assertEquals("", PolicyMatchNormalize.normalizeSubject("  "));
    }
}
