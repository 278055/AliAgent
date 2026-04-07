package com.bn.aliagent.Controller;

import com.bn.aliagent.common.R;
import com.bn.aliagent.context.UserContext;
import com.bn.aliagent.service.AuthService;
import com.bn.aliagent.service.TokenService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口控制器（RESTful 风格）
 *
 * 提供用户注册、登录、修改密码、退出登录等接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    /**
     * 用户注册
     *
     * POST /api/auth/register
     * Body: { "username": "xxx", "password": "xxx", "email": "xxx" }
     */
    @PostMapping("/register")
    public R<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return R.fail(400, "用户名和密码不能为空");
        }

        String userId = authService.register(username, password, email);
        if (userId == null) {
            return R.fail(409, "用户名已存在");
        }
        return R.ok("注册成功", Map.of("userId", userId));
    }

    /**
     * 用户登录
     *
     * POST /api/auth/login
     * Body: { "username": "xxx", "password": "xxx" }
     */
    @PostMapping("/login")
    public R<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return R.fail(400, "用户名和密码不能为空");
        }

        String token = authService.login(username, password);
        if (token == null) {
            return R.fail(401, "用户名或密码错误");
        }
        return R.ok("登录成功", Map.of("token", token));
    }

    /**
     * 修改密码（需已登录）
     *
     * PUT /api/auth/password
     * Header: Authorization: Bearer {token}
     * Body: { "oldPassword": "xxx", "newPassword": "xxx" }
     */
    @PutMapping("/password")
    public R<?> changePassword(@RequestBody Map<String, String> body) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录或 Token 已过期");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            return R.fail(400, "旧密码和新密码不能为空");
        }

        boolean ok = authService.changePassword(userId, oldPassword, newPassword);
        if (!ok) {
            return R.fail(400, "旧密码不正确");
        }
        return R.ok("密码修改成功");
    }

    /**
     * 退出登录
     *
     * DELETE /api/auth/logout
     * Header: Authorization: Bearer {token}
     */
    @DeleteMapping("/logout")
    public R<?> logout() {
        String userId = UserContext.getUserId();
        if (userId != null) {
            tokenService.deleteToken(userId);
        }
        return R.ok("退出成功");
    }
}
