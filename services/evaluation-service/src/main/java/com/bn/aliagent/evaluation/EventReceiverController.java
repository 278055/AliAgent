package com.bn.aliagent.evaluation;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventReceiverController {
    private static final Logger log = LoggerFactory.getLogger(EventReceiverController.class);
    @PostMapping("/api/v1/events") @ResponseStatus(HttpStatus.ACCEPTED)
    void receive(@RequestBody Map<String, Object> event) { log.info("接收评测事件占位请求 eventId={}, traceId={}, tenantId={}", event.get("eventId"), event.get("traceId"), event.get("tenantId")); }
}
