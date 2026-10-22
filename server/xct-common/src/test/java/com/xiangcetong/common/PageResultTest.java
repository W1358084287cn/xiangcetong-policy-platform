package com.xiangcetong.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页结果：页码/每页条数规范化、offset 计算、空列表兜底。
 */
class PageResultTest {

    @Test
    void normalizePageNumDefaultsToOne() {
        assertEquals(1, PageResult.normalizePageNum(null));
        assertEquals(1, PageResult.normalizePageNum(0));
        assertEquals(1, PageResult.normalizePageNum(-5));
        assertEquals(3, PageResult.normalizePageNum(3));
    }

    @Test
    void normalizePageSizeDefaultsToTenAndCapsAtFifty() {
        assertEquals(10, PageResult.normalizePageSize(null));
        assertEquals(10, PageResult.normalizePageSize(0));
        assertEquals(10, PageResult.normalizePageSize(-1));
        assertEquals(50, PageResult.normalizePageSize(100));
        assertEquals(20, PageResult.normalizePageSize(20));
    }

    @Test
    void offsetCalculation() {
        assertEquals(0, PageResult.offset(1, 10));
        assertEquals(10, PageResult.offset(2, 10));
        assertEquals(45, PageResult.offset(2, 45));
    }

    @Test
    void constructorNullListBecomesEmpty() {
        PageResult<String> r = new PageResult<>(null, 0, 1, 10);
        assertTrue(r.getList().isEmpty());
    }

    @Test
    void constructorKeepsValues() {
        PageResult<String> r = new PageResult<>(List.of("a"), 42, 2, 5);
        assertEquals(1, r.getList().size());
        assertEquals(42, r.getTotal());
        assertEquals(2, r.getPageNum());
        assertEquals(5, r.getPageSize());
    }
}
