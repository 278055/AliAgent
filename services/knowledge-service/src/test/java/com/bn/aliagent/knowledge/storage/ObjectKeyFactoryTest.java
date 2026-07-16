package com.bn.aliagent.knowledge.storage;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObjectKeyFactoryTest {
    private final ObjectKeyFactory factory = new ObjectKeyFactory();

    @Test
    void 对象键必须以所属租户前缀开头且租户间隔离() {
        String tenantA = factory.create("tenant-a", "报价 单.pdf");
        String tenantB = factory.create("tenant-b", "报价 单.pdf");

        assertTrue(tenantA.startsWith("knowledge/tenant-tenant-a/"));
        assertTrue(tenantB.startsWith("knowledge/tenant-tenant-b/"));
        assertNotEquals(tenantA, tenantB);
    }
}
