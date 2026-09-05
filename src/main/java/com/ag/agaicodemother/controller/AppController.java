package com.ag.agaicodemother.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ag.agaicodemother.ai.AiCodeGeneratorService;
import com.ag.agaicodemother.annotation.AuthCheck;
import com.ag.agaicodemother.common.BaseResponse;
import com.ag.agaicodemother.common.DeleteRequest;
import com.ag.agaicodemother.common.ResultUtils;
import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.constant.UserConstant;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.exception.ThrowUtils;
import com.ag.agaicodemother.model.dto.app.*;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.enums.AppGenStatusEnum;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import com.ag.agaicodemother.model.enums.AppVisibilityEnum;
import com.ag.agaicodemother.model.vo.AppVO;
import com.ag.agaicodemother.model.vo.AppVersionVO;
import com.ag.agaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import com.ag.agaicodemother.model.entity.App;
import com.ag.agaicodemother.service.AppService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
@RestController
@RequestMapping("/app")
@Slf4j
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;



    /**
     * 应用聊天生成代码（流式 SSE）
     *
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);
        // 返回一个响应式流 Flux<ServerSentEvent<String>>，用于 SSE 推送
        return contentFlux
                // 对上游发出的每个字符串片段进行处理
                .map(chunk -> {
                    // 将当前文本片段包装成一个 Map 结构，键为 "d"，值为当前片段，用于后续 JSON 序列化
                    Map<String, String> wrapper = Map.of("d", chunk);
                    // 使用 Hutool 的 JSONUtil 将 Map 转换为 JSON 字符串
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    // 构建一个 ServerSentEvent 对象，数据类型为 String
                    return ServerSentEvent.<String>builder()
                            // 设置事件的数据内容为刚才生成的 JSON 字符串
                            .data(jsonData)
                            // 完成构建
                            .build();
                })
                // 在主内容流结束后，拼接一个结束事件
                //Mono.just(value) 是创建 Mono 的工厂方法：把一个已经算好的值包装成一个只发一次、发完立即结束的 Mono
                .concatWith(Mono.just(
                        // 构建一个表示生成结束的 ServerSentEvent
                        ServerSentEvent.<String>builder()
                                // 设置事件名称为 "done"，客户端可监听此事件来判断流结束
                                .event("done")
                                // 设置数据为空字符串，表示无具体内容
                                .data("")
                                // 完成构建
                                .build()
                ));
    }

    /**
     * 创建应用
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 调用大模型根据初始描述自动生成应用名称（失败时兜底为 initPrompt 前 12 位）
        app.setAppName(appService.generateAppNameByAi(initPrompt));
        // 暂时设置为多文件生成
        app.setCodeGenType(CodeGenTypeEnum.MULTI_FILE.getValue());
        // ==================== 可见范围处理 ====================
        String visibility = appAddRequest.getVisibility();
        if (StrUtil.isBlank(visibility)) {
            visibility = AppConstant.DEFAULT_APP_VISIBILITY;
        }
        ThrowUtils.throwIf(AppVisibilityEnum.getEnumByValue(visibility) == null,
                ErrorCode.PARAMS_ERROR, "可见范围参数错误，仅支持 public / private");
        // 将校验通过的可见范围写入待入库的应用对象
        app.setVisibility(visibility);
        // ==================== 生成状态初始化（新增） ====================
        // 新应用尚未生成过代码，显式置为"未开始"
        // （数据库有默认值，这里显式赋值是为了让代码意图更清晰，不依赖 DB 兜底）
        app.setGenStatus(AppGenStatusEnum.NOT_START.getValue());
        // ==================== 标签处理（新增） ====================
        // 校验并规范化标签（null 直接通过，表示不带标签创建）
        String tags = appService.validateAndNormalizeTags(appAddRequest.getTags());
        // 将规范化的标签串写入待入库对象（可能为 null，数据库存 null）
        app.setTags(tags);
        // 插入数据库
        boolean result = appService.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(app.getId());
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // ==================== 可见范围更新 ====================
        String visibility = appUpdateRequest.getVisibility();
        if (StrUtil.isNotBlank(visibility)) {
            ThrowUtils.throwIf(AppVisibilityEnum.getEnumByValue(visibility) == null,
                    ErrorCode.PARAMS_ERROR, "可见范围参数错误，仅支持 public / private");
            app.setVisibility(visibility);
        }
        // ==================== 标签更新（新增） ====================
        // 用户传了标签才处理（没传表示本次不修改标签）
        if (appUpdateRequest.getTags() != null) {
            // 校验并规范化标签（空串表示"清空标签"）
            app.setTags(appService.validateAndNormalizeTags(appUpdateRequest.getTags()));
        }
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 查看应用的历史版本号列表
     *
     * @param appId   应用 ID
     * @param request 请求对象（用于获取登录用户）
     * @return 版本列表（按版本号倒序，含当前版本标记）
     */
    @GetMapping("/version/list")
    public BaseResponse<List<AppVersionVO>> listAppVersions(@RequestParam Long appId, HttpServletRequest request) {
        // 参数校验：应用 ID 不能为空或非法
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户（权限校验在服务层进行）
        User loginUser = userService.getLoginUser(request);
        // 调用服务层获取版本列表并包装为统一响应
        return ResultUtils.success(appService.listAppVersions(appId, loginUser));
    }

    /**
     * 回退应用版本
     * 回退 = 文件 + 版本号一起回退：目标版本的文件被复制为新版本，currentVersion 指向它，
     * 已部署的应用还会自动同步部署目录，前端立即展示对应版本的页面
     *
     * @param appRollbackRequest 回退请求（appId + 目标版本号）
     * @param request            请求对象（用于获取登录用户）
     * @return 回退后产生的新版本号
     */
    @PostMapping("/version/rollback")
    public BaseResponse<Integer> rollbackApp(@RequestBody AppRollbackRequest appRollbackRequest, HttpServletRequest request) {
        // 参数校验：请求体不能为空
        ThrowUtils.throwIf(appRollbackRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户（权限校验在服务层进行）
        User loginUser = userService.getLoginUser(request);
        // 调用服务层执行复制式回退，返回回退后产生的新版本号
        Integer newVersion = appService.rollbackApp(appRollbackRequest.getAppId(), appRollbackRequest.getTargetVersion(), loginUser);
        return ResultUtils.success(newVersion);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取应用详情（含可见范围校验，保护用户隐私）
     * 权限规则：
     * 1. 应用创建者本人：可以查看自己的应用（无论公开/私有）；
     * 2. 管理员：可以查看所有应用；
     * 3. 其他用户/游客：只能查看"公开"的应用，访问私有应用返回无权限错误。
     *
     * @param id      应用 id
     * @param request 请求对象（用于获取当前登录用户，未登录按游客处理）
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id, HttpServletRequest request) {
        // 参数校验：应用 id 必须大于 0
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库：根据 id 获取应用实体
        App app = appService.getById(id);
        // 应用不存在时抛出"数据不存在"异常
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // ==================== 可见范围权限校验 ====================
        User loginUser = null;
        try {
            loginUser = userService.getLoginUser(request);
        } catch (BusinessException e) {
        }
        boolean isOwner = loginUser != null && app.getUserId().equals(loginUser.getId());
        boolean isAdmin = loginUser != null && UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!isOwner && !isAdmin && AppConstant.APP_VISIBILITY_PRIVATE.equals(app.getVisibility())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "该应用为私有应用，无权查看");
        }
        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVo(app));
    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVoList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表（含可见范围过滤，保护用户隐私）
     * 权限规则：
     * 1. 管理员：查看所有精选应用（含私有的）；
     * 2. 普通用户：只能看到"公开"的精选应用 + 自己创建的私有精选应用；
     * 3. 游客（未登录）：只能看到"公开"的精选应用。
     *
     * @param appQueryRequest 查询请求
     * @param request         请求对象（用于识别当前用户身份，未登录按游客处理）
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                         HttpServletRequest request) {
        // 参数校验：请求体不能为空
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个，防止一次性拉取过多数据
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        // 取出页码（第几页）
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选的应用：把查询条件中的优先级固定为精选优先级 99
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        // 根据查询请求构造基础查询条件（名称模糊、类型、优先级等）
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // ==================== 可见范围权限过滤 ====================
        User loginUser = null;
        try {
            loginUser = userService.getLoginUser(request);
        } catch (BusinessException e) {
        }
        boolean isAdmin = loginUser != null && UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!isAdmin) {
            if (loginUser != null) {
                queryWrapper.eq("userId", loginUser.getId());
            } else {
                queryWrapper.eq("visibility", AppConstant.APP_VISIBILITY_PUBLIC);
            }
        }
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装：把应用实体列表转换为应用 VO 列表（附带创建者信息）
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVoList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        // ==================== 可见范围校验 ====================
        String visibility = appAdminUpdateRequest.getVisibility();
        if (StrUtil.isNotBlank(visibility)) {
            ThrowUtils.throwIf(AppVisibilityEnum.getEnumByValue(visibility) == null,
                    ErrorCode.PARAMS_ERROR, "可见范围参数错误，仅支持 public / private");
        }
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVoList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVo(app));
    }

    /**
     * 置顶应用（应用置顶功能新增）
     * 实现方式：把应用的优先级设为 999（PINNED_APP_PRIORITY），
     * 列表按优先级倒序排列时，置顶应用自然排在最前面
     * 权限：仅应用创建者本人或管理员可置顶
     *
     * @param appId   要置顶的应用 id
     * @param request 请求对象（用于获取登录用户）
     * @return 置顶结果
     */
    @PostMapping("/pin")
    public BaseResponse<Boolean> pinApp(@RequestParam Long appId, HttpServletRequest request) {
        // 参数校验：应用 id 必须非空且合法
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 查询目标应用（getById 自动过滤已逻辑删除的数据）
        App app = appService.getById(appId);
        // 应用不存在时抛出"数据不存在"异常
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 权限校验：仅创建者本人或管理员可以置顶
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅应用创建者或管理员可以置顶应用");
        }
        // 构造待更新对象：只设置 id 和新优先级，其余字段为 null 不会被更新
        App updateApp = new App();
        // 定位要更新的行
        updateApp.setId(appId);
        // 核心动作：优先级设为 999，排序时排到最前
        updateApp.setPriority(AppConstant.PINNED_APP_PRIORITY);
        // 记录本次编辑时间
        updateApp.setEditTime(LocalDateTime.now());
        // 执行更新（MyBatis-Flex 默认忽略 null 字段，只更新 priority 和 editTime 两列）
        boolean result = appService.updateById(updateApp);
        // 更新失败（影响行数为 0）抛操作异常
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "置顶失败");
        // 返回成功
        return ResultUtils.success(true);
    }

    /**
     * 取消置顶（应用置顶功能新增）
     * 实现方式：把优先级恢复为默认值 0（DEFAULT_APP_PRIORITY）
     * 权限：仅应用创建者本人或管理员可操作
     * 说明：如果该应用同时是精选（99），取消置顶后会被重置为 0，
     * 需要保留精选身份的话由管理员重新在管理后台设置优先级即可
     *
     * @param appId   要取消置顶的应用 id
     * @param request 请求对象（用于获取登录用户）
     * @return 取消置顶结果
     */
    @PostMapping("/unpin")
    public BaseResponse<Boolean> unpinApp(@RequestParam Long appId, HttpServletRequest request) {
        // 参数校验：应用 id 必须非空且合法
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 查询目标应用
        App app = appService.getById(appId);
        // 应用不存在时抛出"数据不存在"异常
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 权限校验：仅创建者本人或管理员可以取消置顶
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅应用创建者或管理员可以取消置顶");
        }
        // 构造待更新对象
        App updateApp = new App();
        // 定位要更新的行
        updateApp.setId(appId);
        // 把优先级恢复为默认值 0，应用回到普通排序位置
        updateApp.setPriority(AppConstant.DEFAULT_APP_PRIORITY);
        // 记录本次编辑时间
        updateApp.setEditTime(LocalDateTime.now());
        // 执行更新
        boolean result = appService.updateById(updateApp);
        // 更新失败抛操作异常
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "取消置顶失败");
        // 返回成功
        return ResultUtils.success(true);
    }

    /**
     * 下线应用（部署控制功能新增）
     * 下线 = 删除部署目录文件 + 部署状态置为 offline，部署 URL 立即失效（404）；
     * deployKey 保留，之后重新调用部署接口可恢复上线且 URL 不变。
     * 权限：仅应用创建者本人或管理员可下线
     *
     * @param appId   要下线的应用 id
     * @param request 请求对象（用于获取登录用户）
     * @return 下线结果
     */
    @PostMapping("/undeploy")
    public BaseResponse<Boolean> undeployApp(@RequestParam Long appId, HttpServletRequest request) {
        // 参数校验：应用 id 必须非空且合法
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 委托服务层执行下线（内部完成权限校验、目录删除、状态更新）
        appService.undeployApp(appId, loginUser);
        // 包装为统一成功响应返回
        return ResultUtils.success(true);
    }
}
