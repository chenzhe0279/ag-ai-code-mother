package com.ag.agaicodemother.service;

import com.ag.agaicodemother.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 内容安全检测服务：静态关键词 + AI 大模型双层检测
 */
public interface ContentSafetyService {

    /**
     * 对用户消息做内容安全检测，检测到敏感内容时抛出 BusinessException 拦截请求
     *
     * @param message   用户输入消息
     * @param loginUser 当前登录用户
     * @param appId     应用 id
     * @param request   请求对象（用于获取客户端 IP）
     */
    void checkContentSafety(String message, User loginUser, Long appId, HttpServletRequest request);
}