package com.bn.aliagent.insight;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import com.bn.aliagent.insight.intake.InsightIntakeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventReceiverController {
    private final InsightIntakeService intake;
    EventReceiverController(InsightIntakeService intake) { this.intake = intake; }
    @PostMapping("/api/v1/events") @ResponseStatus(HttpStatus.ACCEPTED)
    void receive(@RequestBody InsightEventEnvelope event, @RequestHeader("X-Tenant-Id") String tenantId) { intake.accept(event, tenantId); }
}
