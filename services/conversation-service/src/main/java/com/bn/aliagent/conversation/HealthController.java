package com.bn.aliagent.conversation;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    @GetMapping("/api/v1/health") Map<String, Object> health() { return Map.of("code", 200, "message", "", "data", Map.of("status", "UP", "service", "conversation-service")); }
}
