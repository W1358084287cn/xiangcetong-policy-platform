package com.xiangcetong.policy.support;

import java.util.List;
import java.util.Locale;

/**
 * 政策匹配用的种养/主体归一（纯函数，便于单测）。
 * 与 SQL JSON_CONTAINS 大写码约定一致。
 */
public final class PolicyMatchNormalize {

    private PolicyMatchNormalize() {
    }

    /**
     * 种养中文/枚举归一成政策 JSON 用的大写码（RICE/LIVESTOCK 等）。
     */
    public static String normalizePlant(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        String upper = t.toUpperCase(Locale.ROOT);
        if (List.of("RICE", "TEA", "VEGETABLE", "FRUIT", "LIVESTOCK",
                "AQUACULTURE", "MACHINERY", "OTHER").contains(upper)) {
            return upper;
        }
        if (t.contains("鱼") || t.contains("水产") || t.contains("虾") || t.contains("蟹")) {
            return "AQUACULTURE";
        }
        if (t.contains("畜") || t.contains("禽") || t.contains("猪") || t.contains("牛")
                || t.contains("羊") || t.contains("鸡") || t.contains("鸭")
                || (t.contains("养殖") && !t.contains("水产"))) {
            return "LIVESTOCK";
        }
        if (t.contains("茶")) {
            return "TEA";
        }
        if (t.contains("菜")) {
            return "VEGETABLE";
        }
        if (t.contains("果")) {
            return "FRUIT";
        }
        if (t.contains("农机") || t.contains("机械")) {
            return "MACHINERY";
        }
        if (t.contains("稻") || t.contains("小麦") || t.contains("玉米")
                || t.contains("大豆") || t.contains("粮") || t.contains("谷物")) {
            return "RICE";
        }
        return "OTHER";
    }

    /**
     * 经营主体归一：英文枚举直接认，中文农户/家庭农场等映射到 FARMER 等。
     */
    public static String normalizeSubject(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim();
        String upper = t.toUpperCase(Locale.ROOT);
        if (List.of("FARMER", "FAMILY_FARM", "COOP", "ENTERPRISE").contains(upper)) {
            return upper;
        }
        if (t.contains("家庭农场")) {
            return "FAMILY_FARM";
        }
        if (t.contains("合作社") || t.contains("合作") || t.contains("联社")) {
            return "COOP";
        }
        if (t.contains("企业") || t.contains("公司")) {
            return "ENTERPRISE";
        }
        if (t.contains("普通农户") || t.contains("农户") || t.contains("个体") || t.contains("散户")) {
            return "FARMER";
        }
        return upper;
    }
}
