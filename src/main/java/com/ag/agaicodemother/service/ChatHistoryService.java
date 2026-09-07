package com.ag.agaicodemother.service;

import com.ag.agaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.ag.agaicodemother.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.ag.agaicodemother.model.entity.ChatHistory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存对话消息
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 删除对话消息
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 获取分页查询历史消息记录
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 游标查询包装类
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
