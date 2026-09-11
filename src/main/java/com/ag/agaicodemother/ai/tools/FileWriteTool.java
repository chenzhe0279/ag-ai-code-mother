package com.ag.agaicodemother.ai.tools;

import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 * 写入路径遵循版本化目录约定：tmp/code_output/vue_project_{appId}/v{version}/
 * 每个版本写入独立子目录（v1、v2、v3...），互不覆盖，支持历史版本回退
 */
@Slf4j
public class FileWriteTool {

    /**
     * 本次生成对应的版本号
     * 由外层在 reserveNextVersion 预留版本号后通过构造器注入，
     * 保证工具写入的文件落在 v{version} 隔离目录，不会覆盖历史版本。
     * 注意：版本号是系统分配的敏感信息，绝不能作为 @Tool 参数让 AI 自行指定
     */
    private final Integer version;

    /**
     * 构造工具实例（每次生成会话创建一个新实例，绑定当次预留的版本号）
     *
     * @param version 预留的版本号（reserveNextVersion 返回值）
     */
    public FileWriteTool(Integer version) {
        // 版本号为空或非正数说明调用方传参错误，快速失败防止写到错误目录
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("版本号不能为空且必须为正数");
        }
        this.version = version;
    }

    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径") String relativeFilePath, @P("要写入文件的内容") String content, @ToolMemoryId Long appId) {
        // 参数兜底：DeepSeek 偶发漏传 arguments 字段（此时参数为 null）。
        // 若在这里抛 NPE，langchain4j 会把 NPE 的 message（null）当作工具结果写回记忆，
        // 随即抛 "text cannot be null or blank" 导致整个生成流中断
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "参数错误：relativeFilePath 不能为空，请重新调用 writeFile 并传入完整参数";
        }
        if (content == null || content.isBlank()) {
            return "参数错误：content 不能为空，请重新调用 writeFile 写入 " + relativeFilePath + " 并包含完整文件内容";
        }
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                // 相对路径处理：构建版本化的项目目录
                // 目录结构：tmp/code_output/vue_project_{appId}/v{version}
                // 版本子目录名：v1、v2、v3...，每个版本互不覆盖
                String projectDirName = CodeGenTypeEnum.VUE_PROJECT.getValue() + "_" + appId;
                String versionDirName = AppConstant.CODE_VERSION_DIR_PREFIX + version;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName, versionDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            // 兜底 Exception 而非仅 IOException：任何异常都必须返回非空文本，
            // 否则 langchain4j 拿到 null 结果会在写回记忆时抛 ensureNotBlank 异常中断生成
            String errorMessage = "文件写入失败: " + relativeFilePath
                    + ", 错误: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
}
