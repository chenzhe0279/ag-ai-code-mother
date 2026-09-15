package com.ag.agaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.exception.ThrowUtils;
import com.ag.agaicodemother.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {
    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );
    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 基础校验
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "项目目录不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "指定路径不是目录");
        log.info("开始打包下载项目: {} -> {}.zip", projectPath, downloadFileName);
        // 设置 HTTP 响应头
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.addHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        //设置文件过滤器
        // 这里是构造文件过滤器，file 是当前遍历到的文件或目录（File 对象）。
        FileFilter fileFilter = file ->(isPathAllowed(projectDir.toPath(), file.toPath()));
        try {
            // 使用 Hutool 的 ZipUtil 直接将过滤后的目录压缩到响应输出流
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, fileFilter, projectDir);
            log.info("项目打包下载完成: {}", downloadFileName);
        } catch (Exception e) {
            log.error("项目打包下载异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
        }
    }
    /**
     * 检查路径是否允许包含在压缩包中
     *
     * @param projectRoot 项目根目录
     * @param fullPath    完整路径
     * @return 是否允许
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 计算 fullPath 相对于 projectRoot 的相对路径。
        // 容易混淆的点：
        // projectRoot.relativize(fullPath) 中，调用方 projectRoot 是“基准路径/起点”，
        // 参数 fullPath 是“目标路径”。结果是“从 projectRoot 到 fullPath 的相对路径”。
        //
        // 示例：
        // projectRoot  = /project/my-app
        // fullPath     = /project/my-app/src/main/App.java
        // relativePath = src/main/App.java
        //
        // 如果反过来写成 fullPath.relativize(projectRoot)，方向就反了，
        // 会得到类似 ../../.. 这样的路径。
        //
        // 后面遍历 relativePath 的每一段，相当于从项目根目录开始逐层检查
        // 目录名/文件名是否需要被过滤。
        Path relativePath = projectRoot.relativize(fullPath);
        for (Path path : relativePath) {
            String pathName = path.toString();
            // 检查是否在忽略名称列表中
            if (IGNORED_NAMES.contains(pathName)) {
                return false;
            }
            // 检查文件扩展名
            if (IGNORED_EXTENSIONS.stream().anyMatch(pathName::endsWith)) {
                return false;
            }
        }
        return true;
    }
}
