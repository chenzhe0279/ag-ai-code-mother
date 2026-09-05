package com.ag.agaicodemother.service;

import com.ag.agaicodemother.model.dto.app.AppQueryRequest;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.vo.AppVO;
import com.ag.agaicodemother.model.vo.AppVersionVO;
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
     *
     * @param appId     应用ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return 流式生成结果
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 部署应用
     *
     * @param appId     应用ID
     * @param loginUser 登录用户
     * @return 部署后可访问的 URL
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 下线应用（部署控制功能新增）
     * 下线 = 删除部署目录文件 + 部署状态置为 offline；
     * deployKey 保留在数据库中，重新部署后 URL 保持不变
     *
     * @param appId     应用ID
     * @param loginUser 登录用户（权限校验：仅本人或管理员可下线）
     */
    void undeployApp(Long appId, User loginUser);

    /**
     * 查看应用的历史版本号列表（版本化功能一）
     *
     * @param appId     应用ID
     * @param loginUser 登录用户（权限校验：仅本人或管理员可查看）
     * @return 版本列表（按版本号倒序，含当前版本标记）
     */
    List<AppVersionVO> listAppVersions(Long appId, User loginUser);

    /**
     * 回退到历史版本（版本化功能二，复制式回退）
     * 回退会同时生效两件事：版本号 + 对应的代码文件（复制目标版本为新版本），
     * 若应用已部署过还会自动同步部署目录，前端能立即展示对应版本的页面
     *
     * @param appId         应用ID
     * @param targetVersion 目标版本号
     * @param loginUser     登录用户（权限校验：仅本人或管理员可回退）
     * @return 回退后产生的新版本号
     */
    Integer rollbackApp(Long appId, Integer targetVersion, User loginUser);

    /**
     * 获取应用封装类
     *
     * @param app 应用实体
     * @return 应用封装类
     */
    AppVO getAppVo(App app);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件包装器
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 批量获取应用信息
     *
     * @param appList 应用列表
     * @return 应用封装类列表
     */
    List<AppVO> getAppVoList(List<App> appList);

    /**
     * 调用AI工具生成应用名称
     * @param initPrompt
     * @return
     */
    String generateAppNameByAi(String initPrompt);

    /**
     * 验证并规范标签格式
     * @param tags
     * @return
     */
    String validateAndNormalizeTags(String tags);

    /**
     * 删除应用并关联清理数据（数据清理功能新增）
     * 删除 = 逻辑删除数据库记录 + 删除磁盘上的版本目录和部署目录：
     * 1. 版本目录 tmp/code_output/{codeType}_{appId}/：AI 生成的全部历史版本代码；
     * 2. 部署目录 tmp/code_deploy/{deployKey}/：已部署应用的线上文件快照（未部署过则跳过）
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户（权限校验：仅本人或管理员可删除）
     * @return 删除结果
     */
    boolean deleteApp(Long appId, User loginUser);

}
