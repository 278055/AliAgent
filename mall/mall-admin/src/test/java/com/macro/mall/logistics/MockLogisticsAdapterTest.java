package com.macro.mall.logistics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MockLogisticsAdapterTest {
    @Test void returnsDeterministicTrack() {
        MockLogisticsAdapter adapter = new MockLogisticsAdapter();
        assertEquals(adapter.query("test-track-001").get(1).getDescription(), adapter.query("test-track-001").get(1).getDescription());
        assertEquals(3, adapter.query("test-track-001").size());
        assertTrue(adapter.query("").isEmpty());
    }
}
