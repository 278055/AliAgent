package com.macro.mall.logistics;

import java.time.Instant;

public class TrackingPoint {
    private String status;
    private String description;
    private String location;
    private Instant occurredAt;
    public TrackingPoint() { }
    public TrackingPoint(String status, String description, String location, Instant occurredAt) { this.status=status; this.description=description; this.location=location; this.occurredAt=occurredAt; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public Instant getOccurredAt() { return occurredAt; }
}
