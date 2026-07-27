package com.bn.aliagent.evaluation;

import com.bn.aliagent.evaluation.intake.EvaluationEventEnvelope;
import com.bn.aliagent.evaluation.intake.EventIntakeService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventReceiverController {
    private final EventIntakeService intake;
    EventReceiverController(EventIntakeService intake) { this.intake = intake; }
    @PostMapping("/api/v1/events") @ResponseStatus(HttpStatus.ACCEPTED)
    void receive(@RequestBody EvaluationEventEnvelope event, @RequestHeader("X-Tenant-Id") String tenantId) { intake.accept(event, tenantId); }
}
