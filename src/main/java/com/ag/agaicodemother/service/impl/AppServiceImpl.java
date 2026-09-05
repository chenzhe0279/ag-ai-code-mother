package com.ag.agaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.ag.agaicodemother.ai.AiCodeGeneratorService;
import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.constant.UserConstant;
import com.ag.agaicodemother.core.AiCodeGeneratorFacade;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.exception.ThrowUtils;
import com.ag.agaicodemother.model.dto.app.AppQueryRequest;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.enums.AppGenStatusEnum;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import com.ag.agaicodemother.model.vo.AppVO;
import com.ag.agaicodemother.model.vo.AppVersionVO;
import com.ag.agaicodemother.model.vo.UserVO;
import com.ag.agaicodemother.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ag.agaicodemother.model.entity.App;
import com.ag.agaicodemother.mapper.AppMapper;
import com.ag.agaicodemother.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /** 预编译正则：匹配版本目录名 v1、v2、v10...（v 后必须全为数字，防止误匹配其他目录） */
    private static final Pattern VERSION_DIR_PATTERN = Pattern.compile("^v(\\d+)$");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        //1.权限校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(message == null || message.isEmpty(), ErrorCode.PARAMS_ERROR, "提示词信息不能为空");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.校验权限，仅本人可以和自己的应用对话
        if(!app.getUserId().equals(loginUser.getId())){
           throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        //4.获取生成的代码枚举类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型不支持");
        }
        //5.分配下一个版本号（版本化新增）
        //   本方法带有 @Transactional（由 Controller 跨类调用，代理生效），
        //   私有方法 reserveNextVersion 在"当前事务"内完成：
        //   行锁串行化并发请求 → 扫描磁盘最大版本目录 +1 → 锁内创建目录 → 写回 currentVersion，
        //   保证并发安全，且指针式回退后再次生成也不会覆盖历史版本
        Integer nextVersion = reserveNextVersion(appId);
        // 在发起 AI 生成前先把状态写入数据库，前端从这一刻起轮询到的是"生成中"
        App updateApp = new App();
        // 定位要更新的行
        updateApp.setId(appId);
        // 设置新的生成状态
        updateApp.setGenStatus(AppGenStatusEnum.GENERATING.getValue());
        // 执行更新
        boolean update = this.updateById(updateApp);
        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "更新应用生成状态失败");
        //6调用AI大模型生成代码
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId ,nextVersion);
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        //2查询App应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        //4.检查是否已存在部署Key（deployKey）
        String deployKey = app.getDeployKey();
        //没有则生成
        if(StrUtil.isBlank(deployKey)){
            deployKey = RandomUtil.randomString(8);
        }
        //5获取应用代码类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        // 当前生效版本号（老数据字段为 null 时兜底为 1）
        int currentVersion = app.getCurrentVersion() == null ? 1 : app.getCurrentVersion();
        // 源目录 = 应用根目录 + v{currentVersion} 版本子目录
        String sourceDirName = codeGenType + "_" + appId + File.separator + AppConstant.CODE_VERSION_DIR_PREFIX + currentVersion;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        //构建 File 对象（仅封装路径，不会在磁盘上创建任何文件/目录）
        File sourceDir = new File(sourceDirPath);
        //6.检查路径是否存在
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        //7.复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey、部署时间和部署状态
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        // 部署控制功能新增：部署成功即视为"已上线"（下线后重新部署也走这里恢复上线）
        updateApp.setDeployStatus(AppConstant.APP_DEPLOY_STATUS_ONLINE);
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回可访问的 URL
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }

    /**
     * 下线应用（部署控制功能新增）
     * 核心动作：
     * 1. 删除部署目录 tmp/code_deploy/{deployKey}/，外部访问部署 URL 立即变为 404；
     * 2. 数据库把部署状态置为 offline；
     * 3. deployKey 保留不删——重新部署时复用同一个 key，URL 保持稳定
     *    （与"固定 deployKey 确保 URL 稳定性"的设计决策保持一致）。
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户（权限校验：仅本人或管理员可下线）
     */
    @Override
    public void undeployApp(Long appId, User loginUser) {
        // 1. 参数校验：应用 id 合法、用户已登录
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：仅创建者本人或管理员可以下线应用
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅应用创建者或管理员可以下线应用");
        }
        // 4. 前置校验：应用必须部署过（deployKey 非空），没部署过就谈不上"下线"
        ThrowUtils.throwIf(StrUtil.isBlank(app.getDeployKey()), ErrorCode.PARAMS_ERROR, "该应用尚未部署，无需下线");
        // 5. 删除部署目录：tmp/code_deploy/{deployKey}/
        //    目录删除后，静态资源接口立即无法命中文件，外部访问返回 404
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey();
        try {
            // FileUtil.del 会递归删除目录及其下所有文件；目录不存在时静默返回（幂等，重复下线不报错）
            FileUtil.del(deployDirPath);
        } catch (Exception e) {
            // 文件删除失败属于系统异常，抛出后由全局异常处理器返回错误信息
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下线失败：" + e.getMessage());
        }
        // 6. 更新数据库：部署状态置为 offline（只更新这一列，其余字段为 null 被忽略）
        App updateApp = new App();
        // 定位要更新的行
        updateApp.setId(appId);
        // 状态置为"已下线"
        updateApp.setDeployStatus(AppConstant.APP_DEPLOY_STATUS_OFFLINE);
        // 执行更新
        boolean updateResult = this.updateById(updateApp);
        // 更新失败抛操作异常（此时目录已删但状态还是 online，出现不一致，提示用户重试即可修复）
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新部署状态失败");
    }

    @Override
    public AppVO getAppVo(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        // 取出可见范围查询条件（可为 null，null 时 MyBatis-Flex 会自动忽略该条件）
        String visibility = appQueryRequest.getVisibility();
        // 取出标签筛选条件（可为 null，null 时 like 条件自动忽略）
        String tagName = appQueryRequest.getTagName();
        // 取出部署状态筛选条件（新增，可为 null，null 时自动忽略）
        String deployStatus = appQueryRequest.getDeployStatus();
        // 取出生成状态筛选条件（新增，可为 null，null 时自动忽略）
        String genStatus = appQueryRequest.getGenStatus();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        // 先构建基础查询条件（不含优先级和排序，后面单独处理）
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                // 按可见范围精确过滤：仅当前端传了 visibility 时该条件才生效
                .eq("visibility", visibility)
                // 按标签筛选：tags 字段做 LIKE 模糊匹配
                .like("tags", tagName)
                // 按部署状态精确过滤（新增）：online/offline
                .eq("deployStatus", deployStatus)
                // 按生成状态精确过滤（新增）：not_start/generating/succeeded/failed
                .eq("genStatus", genStatus)
                .eq("userId", userId);
        // ==================== 优先级过滤（置顶功能改造） ====================
        // 精选列表场景（priority >= 99）：必须用范围查询 >= 99，
        // 否则置顶应用（999）会被 eq(99) 精确匹配排除，从精选列表中消失；
        // 普通场景（priority < 99 或未传）：保持原有的精确匹配逻辑不变
        if (priority != null && priority >= AppConstant.MIN_GOOD_APP_PRIORITY) {
            // 范围过滤：置顶(999) 和精选(99) 的应用都能被查出来
            queryWrapper.ge("priority", priority);
        } else {
            // 精确匹配普通优先级（null 时 MyBatis-Flex 自动忽略该条件）
            queryWrapper.eq("priority", priority);
        }
        // ==================== 排序规则（置顶功能改造） ====================
        // 第一排序键：优先级倒序——置顶(999) > 精选(99) > 普通(0)，置顶应用永远排在最前
        queryWrapper.orderBy("priority", false);
        // 第二排序键：前端指定的排序字段（如 createTime、id），priority 相同时按它排；
        // sortField 为 null 时 MyBatis-Flex 自动忽略该条件
        queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        // 返回构建完成的查询条件
        return queryWrapper;
    }

    @Override
    public List<AppVO> getAppVoList(List<App> appList) {
        if(CollUtil.isEmpty(appList)){
            return new ArrayList<>();
        }
        //批量获取用户信息
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        //根据用户id从数据库中查询用户信息
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream()
                .map(app -> {
                    AppVO appVo = new AppVO();
                    BeanUtil.copyProperties(app, appVo);
                    appVo.setUser(userVOMap.get(app.getUserId()));
                    return appVo;
                }).collect(Collectors.toList());
    }

    /**
     * 查看应用的历史版本号列表（版本化功能一）
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 版本列表（按版本号倒序，最新在前，含当前版本标记）
     */
    @Override
    public List<AppVersionVO> listAppVersions(Long appId, User loginUser) {
        //1.参数校验：应用 id 合法、用户已登录
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.权限校验：仅本人或管理员可查看版本列表
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该应用版本");
        }
        //4.扫描版本目录并封装（按版本号倒序）
        return doListVersions(app);
    }



    /**
     * 回退到历史版本（版本化功能二，指针式回退）
     *
     * 回退 = 直接把 currentVersion 指回目标版本号，不复制文件、不产生新版本：
     * 1. 版本号回退：currentVersion 直接改为 targetVersion；
     * 2. 文件展示：目标版本的文件本来就在磁盘上的 v{targetVersion} 目录里，
     *    静态预览按 currentVersion 定位目录，页面即目标版本的页面（无需复制文件）；
     * 3. 若应用已部署过，自动把目标版本文件重新发布到部署目录，前端"查看作品"同步更新。
     *
     * 历史安全保证：生成新代码时版本号按"磁盘最大版本目录 + 1"分配
     * （见 reserveNextVersion），而不是 currentVersion + 1，
     * 因此回退后 currentVersion 变小也不会导致下次生成覆盖历史版本目录。
     *
     * @param appId         应用 ID
     * @param targetVersion 目标版本号
     * @param loginUser     登录用户
     * @return 回退后的版本号（即目标版本号）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer rollbackApp(Long appId, Integer targetVersion, User loginUser) {
        //1.参数校验：应用 id 与目标版本号均需合法
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(targetVersion == null || targetVersion <= 0, ErrorCode.PARAMS_ERROR, "目标版本号不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.权限校验：仅本人或管理员可回退版本
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限回退该应用版本");
        }
        //4.执行指针式回退（本方法带 @Transactional，DB 更新失败或部署同步失败时会回滚）
        return doRollback(app, targetVersion);
    }

    /**
     * AI 生成应用名称的最大长度（超出部分截断，避免名称过长）
     */
    private static final int AI_APP_NAME_MAX_LENGTH = 20;

    /**
     * 调用大模型根据应用初始描述自动生成应用名称
     * 生成失败或结果为空时，兜底为 initPrompt 前 12 位，保证创建流程不受 AI 波动影响
     *
     * @param initPrompt 应用初始描述
     * @return 应用名称
     */
    @Override
    public String generateAppNameByAi(String initPrompt) {
        // 兜底名称：沿用旧逻辑取 initPrompt 前 12 位
        String fallbackName = initPrompt.substring(0, Math.min(initPrompt.length(), 12));
        try {
            // 调用 AI 服务生成名称
            String appNameResult = aiCodeGeneratorService.generateAppName(initPrompt).getName();
            // 清洗 AI 输出：去引号、去空白、只取第一行
            String aiName = appNameResult == null ? null : cleanAiAppName(appNameResult);
            // 输出为空则使用兜底名称
            if (StrUtil.isBlank(aiName)) {
                return fallbackName;
            }
            // 超长截断
            return aiName.length() > AI_APP_NAME_MAX_LENGTH
                    ? aiName.substring(0, AI_APP_NAME_MAX_LENGTH) : aiName;
        } catch (Exception e) {
            // AI 调用失败不阻塞创建流程，记录日志后使用兜底名称
            log.warn("AI 生成应用名称失败，使用兜底名称：{}", e.getMessage());
            return fallbackName;
        }
    }

    /**
     * 清洗 AI 生成的应用名称：去首尾空白、去掉包裹引号、只取第一行
     *
     * @param aiName AI 原始输出
     * @return 清洗后的名称
     */
    private String cleanAiAppName(String aiName) {
        if (StrUtil.isBlank(aiName)) {
            return aiName;
        }
        // 只取第一行（模型可能附带解释性文字）
        String name = aiName.split("\\r?\\n")[0].trim();
        // 去掉可能包裹的引号（英文/中文双引号、单引号）
        name = name.replaceAll("[\"“”'‘’]", "");
        return StrUtil.trim(name);
    }


    /**
     * 校验并规范化标签串（标签系统新增，私有辅助方法）
     * 规则：
     * 1. 入参为 null 时返回 null（表示"不设置/不修改标签"）；
     * 2. 空白串视为"清空标签"，返回 null（数据库存 null）；
     * 3. 按逗号切分后：去掉每段首尾空格、过滤空项、去重；
     * 4. 单个标签长度不超过上限，标签总数不超过上限；
     * 5. 返回重新用逗号拼接的规范化标签串，如 "游戏, 工具" → "游戏,工具"。
     *
     * @param tags 前端传入的原始标签串
     * @return 规范化后的标签串；无有效标签时返回 null
     */
    @Override
    public String validateAndNormalizeTags(String tags) {
        // null 表示前端没传，直接返回 null（创建时不设置、更新时不修改）
        if (tags == null) {
            return null;
        }
        // 按英文逗号切分成标签数组（也兼容中文逗号，先统一替换）
        String[] parts = tags.replace("，", ",").split(",");
        // 收集清洗后的有效标签（LinkedHashSet 保持顺序且自动去重）
        Set<String> validTags = new LinkedHashSet<>();
        // 遍历每个切分出来的原始标签片段
        for (String part : parts) {
            // 去掉首尾空格
            String tag = part.trim();
            // 空白片段跳过（如 "a,,b" 中间的空项）
            if (StrUtil.isBlank(tag)) {
                continue;
            }
            // 校验单个标签长度，超长直接报参数错误
            ThrowUtils.throwIf(tag.length() > AppConstant.MAX_TAG_NAME_LENGTH,
                    ErrorCode.PARAMS_ERROR, "单个标签长度不能超过 " + AppConstant.MAX_TAG_NAME_LENGTH + " 个字符");
            // 加入结果集合（Set 自动去重）
            validTags.add(tag);
        }
        // 清洗后没有有效标签：视为清空标签，返回 null
        if (validTags.isEmpty()) {
            return null;
        }
        // 校验标签总数不能超过上限
        ThrowUtils.throwIf(validTags.size() > AppConstant.MAX_APP_TAG_COUNT,
                ErrorCode.PARAMS_ERROR, "每个应用最多设置 " + AppConstant.MAX_APP_TAG_COUNT + " 个标签");
        // 用英文逗号重新拼接为规范化标签串返回，保证入库格式统一
        return String.join(",", validTags);
    }
    // ==================== 版本管理内部私有逻辑（对外不可见） ====================

    /**
     * 分配下一个版本号（并发安全，私有辅助方法）
     *
     * 为什么用"磁盘最大版本目录 + 1"而不是"currentVersion + 1"？
     * 本方案的回退是"指针式回退"——直接把 currentVersion 指回旧的版本号，
     * currentVersion 会变小（如从 v3 回退到 v1）。如果生成时用 currentVersion + 1，
     * 就会算出 v2，而 v2 目录已存在 → 覆盖历史数据。
     * 磁盘上的 v{n} 目录是只增不减的，扫描出的最大版本号永远不会因回退变小，
     * 用它 +1 分配新版本，保证历史目录永不覆盖。
     *
     * 并发分析：
     * 本方法在事务内先 SELECT ... FOR UPDATE 锁定应用行，把同一应用的并发请求串行化；
     * "扫描目录 → 创建 v{next} 目录 → 写回 currentVersion"全部在锁内完成，
     * 下一个并发请求扫描时能看到刚创建的目录，从而拿到不同的版本号。
     *
     * 事务说明：本方法【不标注 @Transactional】！同类内部自调用会绕过 Spring AOP 代理，
     * 注解不会生效。本方法必须由外层带事务的公共方法（chatToGenCode）调用，
     * 行锁在外层事务中生效，事务提交（方法返回）时自动释放。
     *
     * 取舍：版本目录在 AI 生成前就创建好。若生成中途失败，会留下一个空目录、
     * 且 currentVersion 指向它（版本号跳空），这是可接受的——
     * 部署、版本列表、静态访问接口均有存在性校验兜底。
     *
     * @param appId 应用 ID
     * @return 分配的新版本号
     */
    private Integer reserveNextVersion(Long appId) {
        // 行锁查询：SELECT ... FOR UPDATE，并发请求会在此阻塞等待锁释放
        // （MyBatis-Flex 自动拼接逻辑删除条件 isDelete = 0）
        App app = this.getOne(QueryWrapper.create().eq("id", appId).forUpdate());
        // 应用不存在则抛异常（事务回滚，行锁释放）
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 扫描磁盘上已存在的最大版本目录号（历史目录只增不减，不会因指针回退变小）
        int maxVersion = getMaxVersionOnDisk(app);
        // 新版本号 = 磁盘最大版本号 + 1，永不与历史版本冲突
        int nextVersion = maxVersion + 1;
        // 锁内同步创建 v{next} 目录：
        // 后续并发请求的目录扫描能看到它，避免两个请求分配到相同的版本号
        // （目录可能已存在时 mkdir 不会报错，幂等安全）
        FileUtil.mkdir(getVersionDir(app, nextVersion));
        // 写回 currentVersion：版本分配后指针立即指向新版本
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCurrentVersion(nextVersion);
        // MyBatis-Flex 默认忽略 null 字段，只更新 currentVersion 一列
        boolean updateResult = this.updateById(updateApp);
        // 更新失败（影响行数为 0）则抛异常回滚
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "预留版本号失败");
        // 返回分配的新版本号
        return nextVersion;
    }

    /**
     * 扫描磁盘上已存在的最大版本目录号（私有辅助方法）
     * 只在行锁事务内调用（由 reserveNextVersion 调用），用于安全地分配版本号
     *
     * @param app 应用信息
     * @return 最大版本号；没有任何 v{n} 目录时返回 0
     */
    private int getMaxVersionOnDisk(App app) {
        // 应用版本根目录
        File rootDir = new File(getVersionRootDir(app));
        // 列出所有子目录（目录不存在时返回 null）
        File[] children = rootDir.listFiles(File::isDirectory);
        // 最大版本号，默认 0（表示还没有任何版本）
        int maxVersion = 0;
        if (children == null) {
            return maxVersion;
        }
        // 遍历子目录，取符合 v{n} 命名的最大数字
        for (File child : children) {
            Matcher matcher = VERSION_DIR_PATTERN.matcher(child.getName());
            if (matcher.matches()) {
                // 与当前最大值比较，保留更大的
                maxVersion = Math.max(maxVersion, Integer.parseInt(matcher.group(1)));
            }
        }
        return maxVersion;
    }

    private List<AppVersionVO> doListVersions(App app) {
        // 结果容器
        List<AppVersionVO> versionList = new ArrayList<>();
        // 应用根目录
        File rootDir = new File(getVersionRootDir(app));
        // 列出所有子目录
        File[] children = rootDir.listFiles(File::isDirectory);
        // 目录不存在或为空，返回空列表
        if (children == null) {
            return versionList;
        }
        // 遍历子目录，只处理符合 v{n} 命名的版本目录
        for (File child : children) {
            Matcher matcher = VERSION_DIR_PATTERN.matcher(child.getName());
            // 名字不符合 v{n} 格式的目录跳过
            if (!matcher.matches()) {
                continue;
            }
            // 封装版本信息
            AppVersionVO versionVO = new AppVersionVO();
            // 从目录名解析版本号，如 v3 → 3
            versionVO.setVersion(Integer.parseInt(matcher.group(1)));
            // 标记是否为当前生效版本：与数据库 currentVersion 比对
            versionVO.setIsCurrent(versionVO.getVersion().equals(app.getCurrentVersion()));
            // 版本创建时间取目录最后修改时间（即最后一次写入文件的时间）
            LocalDateTime editTime = app.getEditTime();
            versionVO.setCreateTime(editTime);
            versionList.add(versionVO);
        }
        // 按版本号倒序排列，最新版本排在最前面
        versionList.sort(Comparator.comparing(AppVersionVO::getVersion).reversed());
        return versionList;
    }
    /**
     * 执行指针式回退（私有辅助方法，必须在事务内被 rollbackApp 调用）
     *
     * 指针式回退：直接把 currentVersion 指回目标版本号，不复制文件、不产生新版本。
     * 回退后：
     * 1. 静态预览按 currentVersion 定位到 v{targetVersion} 目录，
     *    展示的就是目标版本的文件（文件本来就在磁盘上，无需复制）；
     * 2. 若应用已部署过，把目标版本文件重新发布到部署目录，
     *    前端"查看作品"页面立即展示目标版本对应的页面。
     *
     * 历史安全由"生成时按磁盘最大版本目录 +1 分配"保证（见 reserveNextVersion）：
     * 回退后 currentVersion 变小，但历史目录只增不减，下次生成不会覆盖任何历史版本。
     *
     * @param app           应用信息
     * @param targetVersion 目标版本号
     * @return 回退后的版本号（即目标版本号）
     */
    private Integer doRollback(App app, Integer targetVersion) {
        // 1. 构造目标版本目录并校验真实存在（new File 不校验磁盘，必须 exists + isDirectory）
        File targetDir = getVersionDir(app, targetVersion);
        ThrowUtils.throwIf(!targetDir.exists() || !targetDir.isDirectory(),
                ErrorCode.PARAMS_ERROR, "目标版本不存在: v" + targetVersion);
        // 2. 核心动作：直接把 currentVersion 指回目标版本号
        //    UPDATE 语句本身会对该行加锁，与外层事务配合保证并发安全；
        //    不复制文件、不产生新版本，磁盘上的目标版本文件原地生效
        App updateApp = new App();
        updateApp.setId(app.getId());
        updateApp.setCurrentVersion(targetVersion);
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "回退失败");
        // 3. 若应用之前已经部署过（deployKey 存在），
        //    部署目录 tmp/code_deploy/{deployKey}/ 里还是旧版本的文件快照，
        //    必须把目标版本文件重新发布过去，前端"查看作品"打开的就是回退后的页面
        //    （发布失败抛异常 → 外层事务回滚第 2 步的 DB 更新，保持一致性）
        //    部署控制功能改造：仅当应用处于"已上线"状态才同步部署目录，
        //    已下线（offline）的应用回退版本只更新版本号，不悄悄把文件重新发布到线上
        boolean isOnline = AppConstant.APP_DEPLOY_STATUS_ONLINE.equals(app.getDeployStatus());
        if (StrUtil.isNotBlank(app.getDeployKey()) && isOnline) {
            reDeployToDeployDir(app.getDeployKey(), targetDir);
        }
        // 返回回退后的版本号（即目标版本号，前端可直接展示）
        return targetVersion;
    }

    /**
     * 把指定版本目录重新发布到部署目录（私有辅助方法）
     * 部署目录是按 deployKey 隔离的独立快照，回退后必须手动同步，
     * 否则前端"查看作品"（http://localhost/{deployKey}）展示的还是旧代码
     *
     * @param deployKey        部署标识
     * @param sourceVersionDir 要重新发布的版本目录（v{newVersion}）
     */
    private void reDeployToDeployDir(String deployKey, File sourceVersionDir) {
        // 部署目标目录：tmp/code_deploy/{deployKey}
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            // 把新版本目录的完整内容覆盖复制到部署目录（true = 已存在则覆盖）
            FileUtil.copyContent(sourceVersionDir, new File(deployDirPath), true);
        } catch (Exception e) {
            // 同步失败抛异常，外层事务回滚 currentVersion，
            // 避免出现"版本号已回退但部署页面还是旧代码"的不一致状态
            // 注意：已复制出来的 v{newVersion} 目录无法随数据库事务回滚，
            // 会留下一个孤儿目录，但不影响正确性（版本列表会正常列出它）
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步部署目录失败：" + e.getMessage());
        }
    }
    /**
     * 获取应用版本根目录路径：tmp/code_output/{codeType}_{appId}
     *
     * @param app 应用信息
     * @return 版本根目录绝对路径
     */
    private String getVersionRootDir(App app) {
        // 目录名规则与保存器一致：{codeType}_{appId}
        String sourceDirName = app.getCodeGenType() + "_" + app.getId();
        return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
    }

    /**
     * 获取指定版本目录的 File 对象：{根目录}/v{version}
     * 注意：new File() 只封装路径不做磁盘校验，调用方需自行 exists() 判断
     *
     * @param app     应用信息
     * @param version 版本号
     * @return 版本目录 File 对象
     */
    private File getVersionDir(App app, Integer version) {
        return new File(getVersionRootDir(app) + File.separator + AppConstant.CODE_VERSION_DIR_PREFIX + version);
    }

    /**
     * 删除应用并关联清理数据（数据清理功能新增）
     * 处理顺序（先文件后记录）：
     * 1. 校验应用存在、校验操作权限（仅创建者本人或管理员）；
     * 2. 删除版本目录：AI 生成的所有历史版本代码一次性清掉（v1、v2... 全在应用根目录下）；
     * 3. 删除部署目录：已部署的应用还要清掉线上文件快照，外部 URL 立即 404；
     * 4. 逻辑删除数据库记录。
     * 文件删除失败时只记日志不中断流程：磁盘残留只是空间浪费，
     * 数据库记录的删除才是"删除应用"的主语义，不应被文件系统故障阻塞
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 删除结果
     */
    @Override
    public boolean deleteApp(Long appId, User loginUser) {
        // 1. 参数校验：应用 id 合法、用户已登录
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        // 2. 查询应用信息（getById 自动过滤已逻辑删除的数据）
        App app = this.getById(appId);
        // 应用不存在时抛出"数据不存在"异常
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：仅创建者本人或管理员可以删除应用
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅应用创建者或管理员可以删除应用");
        }
        // 4. 清理版本目录：tmp/code_output/{codeType}_{appId}/
        //    复用已有的 getVersionRootDir 方法拼出应用根目录（包含全部 v{n} 版本子目录）
        cleanDirQuietly(getVersionRootDir(app), "版本目录");
        // 5. 清理部署目录：tmp/code_deploy/{deployKey}/（仅部署过的应用才有）
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            // deployKey 非空说明部署过，拼接部署目录路径并清理
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey();
            // 静默清理：失败只记日志，不阻塞删除主流程
            cleanDirQuietly(deployDirPath, "部署目录");
        }
        // 6. 逻辑删除数据库记录（isDelete 置 1，与项目删除惯例一致）
        return this.removeById(appId);
    }

    /**
     * 静默清理磁盘目录（数据清理功能新增，私有辅助方法）
     * "静默"的含义：目录不存在或删除失败都不抛异常，只记日志——
     * 磁盘清理是尽力而为的附属操作，失败不应影响"删除应用"的主流程
     *
     * @param dirPath 要删除的目录路径
     * @param dirLabel 目录用途标签（仅用于日志区分，如"版本目录"/"部署目录"）
     */
    private void cleanDirQuietly(String dirPath, String dirLabel) {
        try {
            // FileUtil.del 递归删除目录及其下所有文件；目录不存在时静默返回（天然幂等，重复删除不报错）
            FileUtil.del(dirPath);
            // 清理成功记录 info 日志，便于审计追溯
            log.info("应用删除，已清理{}: {}", dirLabel, dirPath);
        } catch (Exception e) {
            // 清理失败只记 warn 日志：残留文件是空间浪费但无害，主删除流程继续执行
            log.warn("应用删除，清理{}失败（残留垃圾文件，不影响删除结果）: {}，原因: {}",
                    dirLabel, dirPath, e.getMessage());
        }
    }
}
