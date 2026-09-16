package com.ag.agaicodemother.ai.tools;

import cn.hutool.json.JSONObject;
import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件读取工具
 * 支持 AI 通过工具调用的方式读取文件内容
 * 读取路径遵循版本化目录约定：tmp/code_output/vue_project_{appId}/v{version}/
 * 工具实例与当次生成的版本号绑定，AI 只能读到本次版本目录内的文件，不会串读历史版本内容
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool{


    @Tool("读取指定路径的文件内容")
    public String readFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId,
            Integer version
    ) {
        // 参数兜底：DeepSeek 偶发漏传 arguments 字段（此时参数为 null）。
        // 若在这里抛 NPE，langchain4j 会把 NPE 的 message（null）当作工具结果写回记忆，
        // 随即抛 "text cannot be null or blank" 导致整个生成流中断
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "参数错误：relativeFilePath 不能为空，请重新调用 readFile 并传入完整参数";
        }
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                // 相对路径处理：构建版本化的项目目录
                // 目录结构：tmp/code_output/vue_project_{appId}/v{version}
                // 版本子目录名：v1、v2、v3...，读取只发生在本次版本目录内
                String projectDirName = CodeGenTypeEnum.VUE_PROJECT.getValue() + "_" + appId;
                String versionDirName = AppConstant.CODE_VERSION_DIR_PREFIX + version;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName, versionDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            // 返回文件内容（非空字符串，满足 langchain4j 工具结果不能为 null 的约束）
            return Files.readString(path);
        } catch (IOException e) {
            String errorMessage = "读取文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
