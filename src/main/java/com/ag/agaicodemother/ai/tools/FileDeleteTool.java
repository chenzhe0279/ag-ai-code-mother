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

/**
 * 文件删除工具
 * 支持 AI 通过工具调用的方式删除文件
 * 删除路径遵循版本化目录约定：tmp/code_output/vue_project_{appId}/v{version}/
 * 工具实例与当次生成的版本号绑定，删除只作用于本次版本目录，不会误删历史版本文件
 */
@Slf4j
public class FileDeleteTool {

    /**
     * 本次生成对应的版本号
     * 由外层在 reserveNextVersion 预留版本号后通过构造器注入，
     * 保证工具删除的文件位于 v{version} 隔离目录，不会误删历史版本。
     * 注意：版本号是系统分配的敏感信息，绝不能作为 @Tool 参数让 AI 自行指定
     */
    private final Integer version;

    /**
     * 构造工具实例（每次生成会话创建一个新实例，绑定当次预留的版本号）
     *
     * @param version 预留的版本号（reserveNextVersion 返回值）
     */
    public FileDeleteTool(Integer version) {
        // 版本号为空或非正数说明调用方传参错误，快速失败防止删到错误目录
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("版本号不能为空且必须为正数");
        }
        this.version = version;
    }

    @Tool("删除指定路径的文件")
    public String deleteFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        // 参数兜底：DeepSeek 偶发漏传 arguments 字段（此时参数为 null）。
        // 若在这里抛 NPE，langchain4j 会把 NPE 的 message（null）当作工具结果写回记忆，
        // 随即抛 "text cannot be null or blank" 导致整个生成流中断
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "参数错误：relativeFilePath 不能为空，请重新调用 deleteFile 并传入完整参数";
        }
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                // 相对路径处理：构建版本化的项目目录
                // 目录结构：tmp/code_output/vue_project_{appId}/v{version}
                // 版本子目录名：v1、v2、v3...，删除只发生在本次版本目录内
                String projectDirName = CodeGenTypeEnum.VUE_PROJECT.getValue() + "_" + appId;
                String versionDirName = AppConstant.CODE_VERSION_DIR_PREFIX + version;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName, versionDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            if (!Files.exists(path)) {
                return "警告：文件不存在，无需删除 - " + relativeFilePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误：指定路径不是文件，无法删除 - " + relativeFilePath;
            }
            // 安全检查：避免删除重要文件
            String fileName = path.getFileName().toString();
            if (isImportantFile(fileName)) {
                return "错误：不允许删除重要文件 - " + fileName;
            }
            Files.delete(path);
            log.info("成功删除文件: {}", path.toAbsolutePath());
            return "文件删除成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "删除文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 判断是否是重要文件，不允许删除
     */
    private boolean isImportantFile(String fileName) {
        String[] importantFiles = {
                "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
                "vite.config.js", "vite.config.ts", "vue.config.js",
                "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json",
                "index.html", "main.js", "main.ts", "App.vue", ".gitignore", "README.md"
        };
        for (String important : importantFiles) {
            if (important.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }
}
