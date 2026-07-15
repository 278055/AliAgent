package com.macro.mall.logistics;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class MockLogisticsAdapter implements LogisticsAdapter {
    @Override public List<TrackingPoint> query(String trackingNo) {
        if (trackingNo == null || trackingNo.trim().isEmpty()) return List.of();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        return List.of(new TrackingPoint("PICKED_UP", "包裹已揽收", "测试仓", base),
                new TrackingPoint("IN_TRANSIT", "包裹运输中", "测试分拨中心", base.plusSeconds(3600)),
                new TrackingPoint("DELIVERED", "包裹已签收", "测试收货点", base.plusSeconds(7200)));
    }
}
