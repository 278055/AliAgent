package com.bn.aliagent.Controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 健康检查接口
 *
 * 专门用于前端检测后端连接状态，不经过 AI 模型，不产生对话记录。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 健康检查接口
     *
     * 返回 "ok"，前端据此判断后端是否在线。
     * 不经过任何 AI 逻辑，不产生数据库记录。
     */
    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "ok";
    }

    /**
     * 流式健康检查（保留，与普通 /stream 接口行为一致）
     */
    @GetMapping(value = "/health/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> healthStream() {
        return Flux.just("ok");
    }
}