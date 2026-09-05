package com.ag.agaicodemother.core.saver;

import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码文件保存执行器
 * 根据代码生成类型执行相应的保存逻辑
 *
 */
public class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();

    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /**
     * 执行代码保存
     *
     * @param codeResult  代码结果对象
     * @param codeGenType 代码生成类型（HTML / MULTI_FILE）
     * @param appId       应用 ID
     * @param version     版本号（透传给模板方法，保存到 v{version} 目录）
     * @return 保存的版本目录
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType , Long appId , Integer version) {
        return switch (codeGenType) {
            // HTML 模式：保存单个 index.html 到 v{version} 目录
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult , appId , version);
            // 多文件模式：保存 index.html + style.css + script.js 到 v{version} 目录
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult , appId , version);
            // 未知类型直接抛异常，防止错误数据落盘
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
