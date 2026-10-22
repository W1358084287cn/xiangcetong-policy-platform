package com.xiangcetong.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机与角色常量：锁定 DB/XML 依赖的字符串字面量，防止重构时无意改动破坏数据一致性。
 */
class StatusConstantsTest {

    @Test
    void applyStatusValuesStable() {
        assertEquals("DRAFT", ApplyStatus.DRAFT);
        assertEquals("PENDING_VILLAGE", ApplyStatus.PENDING_VILLAGE);
        assertEquals("PENDING_TOWN", ApplyStatus.PENDING_TOWN);
        assertEquals("PENDING_DISBURSE", ApplyStatus.PENDING_DISBURSE);
        assertEquals("DISBURSED", ApplyStatus.DISBURSED);
        assertEquals("APPROVED", ApplyStatus.APPROVED);
        assertEquals("REJECTED", ApplyStatus.REJECTED);
    }

    @Test
    void activeStatusesExcludeDraftAndRejected() {
        assertTrue(ApplyStatus.ACTIVE_STATUSES.contains("PENDING_VILLAGE"));
        assertTrue(ApplyStatus.ACTIVE_STATUSES.contains("PENDING_TOWN"));
        assertTrue(ApplyStatus.ACTIVE_STATUSES.contains("PENDING_DISBURSE"));
        assertTrue(ApplyStatus.ACTIVE_STATUSES.contains("DISBURSED"));
        assertTrue(ApplyStatus.ACTIVE_STATUSES.contains("APPROVED"));
        assertTrue(!ApplyStatus.ACTIVE_STATUSES.contains("DRAFT"));
        assertTrue(!ApplyStatus.ACTIVE_STATUSES.contains("REJECTED"));
    }

    @Test
    void roleValuesStable() {
        assertEquals("VILLAGER", Role.VILLAGER);
        assertEquals("VILLAGE_ADMIN", Role.VILLAGE_ADMIN);
        assertEquals("TOWN_ADMIN", Role.TOWN_ADMIN);
        assertEquals("SYS_ADMIN", Role.SYS_ADMIN);
    }

    @Test
    void adminRolesExcludeVillager() {
        assertTrue(Role.ADMIN.contains("VILLAGE_ADMIN"));
        assertTrue(Role.ADMIN.contains("TOWN_ADMIN"));
        assertTrue(Role.ADMIN.contains("SYS_ADMIN"));
        assertTrue(!Role.ADMIN.contains("VILLAGER"));
        assertEquals(4, Role.ALL.size());
    }

    @Test
    void publishAndAuditConstantsStable() {
        assertEquals("ONLINE", PublishStatus.ONLINE);
        assertEquals("OFFLINE", PublishStatus.OFFLINE);
        assertEquals("PASS", AuditAction.PASS);
        assertEquals("REJECT", AuditAction.REJECT);
    }
}
