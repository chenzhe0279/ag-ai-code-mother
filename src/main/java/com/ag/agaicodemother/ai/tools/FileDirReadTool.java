package com.ag.agaicodemother.ai.tools;

import cn.hutool.core.io.FileUtil;
import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * 文件目录读取工具
 * 使用 Hutool 简化文件操作
 * 读取路径遵循版本化目录约定：tmp/code_output/vue_project_{appId}/v{version}/
 * 工具实例与当次生成的版本号绑定，AI 只能看到本次版本目录的项目结构，不会混入历史版本
 */
@Slf4j
public class FileDirReadTool {

    /**
     * 需要忽略的文件和目录
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store",
            ".env", "target", ".mvn", ".idea", ".vscode", "coverage"
    );

    /**
     * 需要忽略的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock"
    );

    /**
     * 本次生成对应的版本号
     * 由外层在 reserveNextVersion 预留版本号后通过构造器注入，
     * 保证目录结构读取自 v{version} 隔离目录，不会把历史版本结构呈现给 AI。
     * 注意：版本号是系统分配的敏感信息，绝不能作为 @Tool 参数让 AI 自行指定
     */
    private final Integer version;

    /**
     * 构造工具实例（每次生成会话创建一个新实例，绑定当次预留的版本号）
     *
     * @param version 预留的版本号（reserveNextVersion 返回值）
     */
    public FileDirReadTool(Integer version) {
        // 版本号为空或非正数说明调用方传参错误，快速失败防止读到错误目录
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("版本号不能为空且必须为正数");
        }
        this.version = version;
    }

    @Tool("读取目录结构，获取指定目录下的所有文件和子目录信息")
    public String readDir(
            @P("目录的相对路径，为空则读取整个项目结构")
            String relativeDirPath,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeDirPath == null ? "" : relativeDirPath);
            if (!path.isAbsolute()) {
                // 相对路径处理：构建版本化的项目目录
                // 目录结构：tmp/code_output/vue_project_{appId}/v{version}
                // 版本子目录名：v1、v2、v3...，结构读取只发生在本次版本目录内
                String projectDirName = CodeGenTypeEnum.VUE_PROJECT.getValue() + "_" + appId;
                String versionDirName = AppConstant.CODE_VERSION_DIR_PREFIX + version;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName, versionDirName);
                path = projectRoot.resolve(relativeDirPath == null ? "" : relativeDirPath);
            }
            File targetDir = path.toFile();
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                return "错误：目录不存在或不是目录 - " + relativeDirPath;
            }
            StringBuilder structure = new StringBuilder();
            structure.append("项目目录结构:\n");
            // 使用 Hutool 递归获取所有文件
            List<File> allFiles = FileUtil.loopFiles(targetDir, file -> !shouldIgnore(file.getName()));
            // 按路径深度和名称排序显示
            allFiles.stream()
                    .sorted((f1, f2) -> {
                        int depth1 = getRelativeDepth(targetDir, f1);
                        int depth2 = getRelativeDepth(targetDir, f2);
                        if (depth1 != depth2) {
                            return Integer.compare(depth1, depth2);
                        }
                        return f1.getPath().compareTo(f2.getPath());
                    })
                    .forEach(file -> {
                        int depth = getRelativeDepth(targetDir, file);
                        String indent = "  ".repeat(depth);
                        structure.append(indent).append(file.getName());
                    });
            return structure.toString();

        } catch (Exception e) {
            String errorMessage = "读取目录结构失败: " + relativeDirPath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 计算文件相对于根目录的深度
     */
    private int getRelativeDepth(File root, File file) {
        Path rootPath = root.toPath();
        Path filePath = file.toPath();
        return rootPath.relativize(filePath).getNameCount() - 1;
    }

    /**
     * 判断是否应该忽略该文件或目录
     */
    private boolean shouldIgnore(String fileName) {
        // 检查是否在忽略名称列表中
        if (IGNORED_NAMES.contains(fileName)) {
            return true;
        }

        // 检查文件扩展名
        return IGNORED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
