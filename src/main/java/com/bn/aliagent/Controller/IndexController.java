package com.bn.aliagent.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径控制器
 *
 * 将根路径 "/" 重定向到 "/index.html"，让用户直接访问 http://localhost:8080 即可打开聊天页面。
 */
@Controller
public class IndexController {

    @GetMapping("/")
    public String index() {
        // 转发到 static/index.html（Spring Boot 默认会从 static/ 目录提供）
        return "forward:/index.html";
    }
}
