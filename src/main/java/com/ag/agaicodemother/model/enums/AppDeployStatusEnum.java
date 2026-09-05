package com.ag.agaicodemother.model.enums;


import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用部署状态枚举（部署控制功能新增）
 * 用于管理应用的上线状态：
 * - ONLINE ：已上线，部署目录中有文件，可通过 URL 正常访问；
 * - OFFLINE：已下线，部署目录文件已删除，URL 访问返回 404，重新部署即可恢复。
 * 枚举风格与项目内 AppVisibilityEnum 保持一致。
 */
@Getter
public enum AppDeployStatusEnum {

    /** 已上线：部署目录中有可访问的文件（值 "online" 为数据库实际存储值） */
    ONLINE("已上线", "online"),

    /** 已下线：部署目录已清空，外部无法访问（值 "offline" 为数据库实际存储值） */
    OFFLINE("已下线", "offline");

    /** 枚举的中文描述文本（可用于前端下拉框/状态标签展示） */
    private final String text;

    /** 枚举的实际取值（数据库存储、前后端传参均使用该值） */
    private final String value;

    /**
     * 枚举构造器（私有，只能由枚举内部调用）
     * @param text  中文描述
     * @param value 实际取值
     */
    AppDeployStatusEnum(String text, String value) {
        // 保存中文描述
        this.text = text;
        // 保存实际取值
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * 用途：对前端传入的部署状态参数做合法性校验，非法值返回 null
     *
     * @param value 枚举值的 value（如 "online"）
     * @return 匹配的枚举值；value 为空或不合法时返回 null
     */
    public static AppDeployStatusEnum getEnumByValue(String value) {
        // 入参为空（null 或空字符串）时直接返回 null，避免后续比较出现空指针
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历该枚举的所有取值
        for (AppDeployStatusEnum anEnum : AppDeployStatusEnum.values()) {
            // 找到 value 与入参一致的枚举项就立即返回
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        // 遍历结束仍未匹配，说明是非法值，返回 null
        return null;
    }
}