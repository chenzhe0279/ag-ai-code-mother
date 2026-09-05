package com.ag.agaicodemother.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用可见范围枚举
 * 用于控制应用对其他用户（非创建者）的可见性，保护用户隐私：
 * - PUBLIC ：公开，所有用户都可以看到；
 * - PRIVATE：私有，仅创建者本人和管理员可以看到。
 * 枚举风格与项目内 UserRoleEnum、CodeGenTypeEnum 保持一致。
 */
@Getter
public enum AppVisibilityEnum {

    /** 公开：应用对所有用户可见（值 "public" 会作为数据库字段的实际取值） */
    PUBLIC("公开", "public"),

    /** 私有：应用仅对创建者本人和管理员可见（值 "private" 会作为数据库字段的实际取值） */
    PRIVATE("私有", "private");

    /** 枚举的中文描述文本（可用于前端下拉框/标签展示） */
    private final String text;

    /** 枚举的实际取值（数据库存储、前后端传参均使用该值） */
    private final String value;

    /**
     * 枚举构造器（私有，只能由枚举内部调用）
     * @param text  中文描述
     * @param value 实际取值
     */
    AppVisibilityEnum(String text, String value) {
        this.text = text;   // 保存中文描述
        this.value = value; // 保存实际取值
    }

    /**
     * 根据 value 获取枚举
     * 用途：对前端传入的可见范围参数做合法性校验，非法值返回 null
     *
     * @param value 枚举值的 value（如 "public"）
     * @return 匹配的枚举值；value 为空或不合法时返回 null
     */
    public static AppVisibilityEnum getEnumByValue(String value) {
        // 入参为空（null 或空字符串）时直接返回 null，避免后续比较出现空指针
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历该枚举的所有取值
        for (AppVisibilityEnum anEnum : AppVisibilityEnum.values()) {
            // 找到 value 与入参一致的枚举项就立即返回
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        // 遍历结束仍未匹配，说明是非法值，返回 null
        return null;
    }
}