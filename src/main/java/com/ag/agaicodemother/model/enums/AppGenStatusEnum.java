package com.ag.agaicodemother.model.enums;


import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 应用生成状态枚举（生成状态功能新增）
 * 记录应用代码生成的实时进度，状态机流转：
 * not_start --(开始生成)--> generating --(流正常结束)--> succeeded
 *                                        └-(流异常中断)--> failed
 * failed/not_start 可再次发起生成，重新进入 generating
 * 枚举风格与项目内 AppVisibilityEnum 保持一致。
 */
@Getter
public enum AppGenStatusEnum {

    /** 未开始：应用刚创建，还没有发起过任何代码生成（值 "not_start" 为数据库实际存储值） */
    NOT_START("未开始", "not_start"),

    /** 生成中：AI 正在流式生成代码（值 "generating" 为数据库实际存储值） */
    GENERATING("生成中", "generating"),

    /** 已成功：最近一次生成已完整结束（值 "succeeded" 为数据库实际存储值） */
    SUCCEEDED("已成功", "succeeded"),

    /** 失败：最近一次生成中途异常中断（值 "failed" 为数据库实际存储值） */
    FAILED("失败", "failed");

    /** 枚举的中文描述文本（可用于前端状态标签展示） */
    private final String text;

    /** 枚举的实际取值（数据库存储、前后端传参均使用该值） */
    private final String value;

    /**
     * 枚举构造器（私有，只能由枚举内部调用）
     * @param text  中文描述
     * @param value 实际取值
     */
    AppGenStatusEnum(String text, String value) {
        // 保存中文描述
        this.text = text;
        // 保存实际取值
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * 用途：对前端传入的生成状态参数做合法性校验，非法值返回 null
     *
     * @param value 枚举值的 value（如 "generating"）
     * @return 匹配的枚举值；value 为空或不合法时返回 null
     */
    public static AppGenStatusEnum getEnumByValue(String value) {
        // 入参为空（null 或空字符串）时直接返回 null，避免后续比较出现空指针
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历该枚举的所有取值
        for (AppGenStatusEnum anEnum : AppGenStatusEnum.values()) {
            // 找到 value 与入参一致的枚举项就立即返回
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        // 遍历结束仍未匹配，说明是非法值，返回 null
        return null;
    }
}