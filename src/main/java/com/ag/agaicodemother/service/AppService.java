package com.ag.agaicodemother.service;

import com.ag.agaicodemother.model.dto.app.AppQueryRequest;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.ag.agaicodemother.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     * @param appId 应用ID
     * @param message 提示词
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 部署应用
     * @param appId
     * @param loginUser
     * @return
     */
    String deployApp(Long appId, User loginUser);
    /**
     * 获取应用封装类
     * @param app
     * @return
     */
    AppVO getAppVo(App app);

    /**
     * 构造应用查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 批量获取应用信息
     * @param appList
     * @return
     */
    List<AppVO> getAppVoList(List<App> appList);


}
