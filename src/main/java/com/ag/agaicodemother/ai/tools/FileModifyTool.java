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
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容
 * 修改路径遵循版本化目录约定：tmp/code_output/vue_project_{appId}/v{version}/
 * 工具实例与当次生成的版本号绑定，修改只作用于本次版本目录，不会污染历史版本文件
 */
@Slf4j
public class FileModifyTool {

    /**
     * 本次生成对应的版本号
     * 由外层在 reserveNextVersion 预留版本号后通过构造器注入，
     * 保证工具修改的文件位于 v{version} 隔离目录，不会误改历史版本。
     * 注意：版本号是系统分配的敏感信息，绝不能作为 @Tool 参数让 AI 自行指定
     */
    private final Integer version;

    /**
     * 构造工具实例（每次生成会话创建一个新实例，绑定当次预留的版本号）
     *
     * @param version 预留的版本号（reserveNextVersion 返回值）
     */
    public FileModifyTool(Integer version) {
        // 版本号为空或非正数说明调用方传参错误，快速失败防止改到错误目录
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("版本号不能为空且必须为正数");
        }
        this.version = version;
    }

    @Tool("修改文件内容，用新内容替换指定的旧内容")
    public String modifyFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        // 参数兜底：DeepSeek 偶发漏传 arguments 字段（此时参数为 null）。
        // 若在 contains/replace 调用上抛 NPE，langchain4j 会把 NPE 的 message（null）
        // 当作工具结果写回记忆，随即抛 "text cannot be null or blank" 导致整个生成流中断
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "参数错误：relativeFilePath 不能为空，请重新调用 modifyFile 并传入完整参数";
        }
        if (oldContent == null || oldContent.isBlank()) {
            return "参数错误：oldContent 不能为空，请重新调用 modifyFile 并传入要替换的旧内容";
        }
        // newContent 允许为空字符串（表示删除旧内容），但绝不允许为 null（replace 会抛 NPE）
        if (newContent == null) {
            return "参数错误：newContent 不能为 null，请重新调用 modifyFile 并传入替换后的新内容";
        }
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                // 相对路径处理：构建版本化的项目目录
                // 目录结构：tmp/code_output/vue_project_{appId}/v{version}
                // 版本子目录名：v1、v2、v3...，修改只发生在本次版本目录内
                String projectDirName = CodeGenTypeEnum.VUE_PROJECT.getValue() + "_" + appId;
                String versionDirName = AppConstant.CODE_VERSION_DIR_PREFIX + version;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName, versionDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            String originalContent = Files.readString(path);
            if (!originalContent.contains(oldContent)) {
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if (originalContent.equals(modifiedContent)) {
                return "信息：替换后文件内容未发生变化 - " + relativeFilePath;
            }
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件: {}", path.toAbsolutePath());
            return "文件修改成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "修改文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
}
