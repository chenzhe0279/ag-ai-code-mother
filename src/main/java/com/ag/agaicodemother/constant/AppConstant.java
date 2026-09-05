package com.ag.agaicodemother.constant;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 版本目录名前缀
     * 每个版本的代码保存在 {应用根目录}/v{n}/ 下，如 v1、v2、v3
     * 版本目录只增不改，是历史版本回退功能的地基
     */
    String CODE_VERSION_DIR_PREFIX = "v";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * 可见范围：公开
     * 公开的应用会展示在首页"精选案例"等公共列表，所有用户均可查看
     */
    String APP_VISIBILITY_PUBLIC = "public";

    /**
     * 可见范围：私有
     * 私有的应用不会出现在别人的公共列表里，只有创建者本人和管理员可以看到
     */
    String APP_VISIBILITY_PRIVATE = "private";

    /**
     * 新建应用的默认可见范围
     * 默认为"公开"，与历史行为保持一致；用户创建后可在编辑页自行切换为"私有"
     */
    String DEFAULT_APP_VISIBILITY = APP_VISIBILITY_PUBLIC;

    /**
     * 单个应用允许拥有的最大标签数量（防止标签滥用，标签过多失去分类意义）
     */
    Integer MAX_APP_TAG_COUNT = 3;

    /**
     * 单个标签名的最大长度（字符数）
     */
    Integer MAX_TAG_NAME_LENGTH = 20;

    /**
     * 置顶应用的优先级（应用置顶功能新增）
     * 优先级体系：置顶 999 > 精选 99 > 普通 0
     * 列表统一按 priority 倒序排列，置顶应用自然排在最前面
     */
    Integer PINNED_APP_PRIORITY = 999;

    /**
     * 置顶应用查询的最小优先级阈值
     * 用于"精选列表"等场景：置顶(999)和精选(99)的应用都要展示，
     * 所以筛选条件为 priority >= 99，而不是精确等于 99
     */
    Integer MIN_GOOD_APP_PRIORITY = GOOD_APP_PRIORITY;

    /**
     * 部署状态：已上线（部署控制功能新增）
     * 部署目录中有文件，外部可通过部署 URL 正常访问
     */
    String APP_DEPLOY_STATUS_ONLINE = "online";

    /**
     * 部署状态：已下线（部署控制功能新增）
     * 部署目录文件已删除，URL 访问返回 404；deployKey 保留，重新部署 URL 不变
     */
    String APP_DEPLOY_STATUS_OFFLINE = "offline";

    /**
     * 新建应用的默认部署状态
     * 新应用尚未部署过，默认视为"已上线"占位值（真正判断是否部署过看 deployKey 是否非空）
     */
    String DEFAULT_APP_DEPLOY_STATUS = APP_DEPLOY_STATUS_ONLINE;

}
