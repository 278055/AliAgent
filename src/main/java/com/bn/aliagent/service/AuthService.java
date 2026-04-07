package com.bn.aliagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bn.aliagent.entity.User;
import com.bn.aliagent.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 认证服务
 *
 * 处理用户注册、登录、修改密码等业务逻辑
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final TokenService tokenService;

    public AuthService(UserMapper userMapper, TokenService tokenService) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
    }

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 密码（明文）
     * @param email    邮箱（可选）
     * @return 注册成功返回 userId，失败返回 null
     */
    public String register(String username, String password, String email) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        Long count = userMapper.selectCount(wrapper);
        if (count > 0) {
            return null; // 用户名已存在
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param username 用户名或邮箱
     * @param password 密码（明文）
     * @return 登录成功返回 Token，失败返回 null
     */
    public String login(String username, String password) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("username", username).or().eq("email", username));
        wrapper.eq("password", password);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            return null; // 用户名或密码错误
        }
        return tokenService.generateToken(user.getId());
    }

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null || !oldPassword.equals(user.getPassword())) {
            return false;
        }
        user.setPassword(newPassword);
        return userMapper.updateById(user) > 0;
    }
}