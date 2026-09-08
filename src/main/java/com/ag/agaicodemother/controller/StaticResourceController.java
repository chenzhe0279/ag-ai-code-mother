package com.ag.agaicodemother.controller;

import com.ag.agaicodemother.constant.AppConstant;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

/**
 * 静态资源访问
 * 用途：把 AI 生成并保存到本地 tmp/code_output 目录下的代码文件提供给浏览器访问
 */
@RestController
@RequestMapping("/static")
@Slf4j
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 目录形式访问（如 /static/{key}/、/static/{key}/v1/）时兜底返回 index.html
            // 版本化改造后预览 URL 带 v{n} 子目录，仅判断 resourcePath.equals("/") 已覆盖不到
            if (file.isDirectory()) {
                file = new File(file, "index.html");
            }
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    // Content-Type 必须按"最终返回的文件"路径计算：目录访问兜底 index.html 后，
                    // 若仍用目录路径（如 .../v1/）判断后缀会落到 application/octet-stream，浏览器会下载而不是渲染
                    .header("Content-Type", getContentTypeWithCharset(file.getPath()))
                    .body(resource);
        } catch (Exception e) {
            // 静默吞异常会让 500 无法排查，这里打印完整堆栈和请求 URI
            log.error("静态资源访问失败, uri={}", request.getRequestURI(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     *
     * @param filePath 本地文件完整路径
     * @return 对应的 MIME 类型字符串
     */
    // 私有辅助方法：只供本类内部调用
    private String getContentTypeWithCharset(String filePath) {
        // HTML 文件：文本类型并显式声明 UTF-8，防止中文乱码
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        // CSS 样式文件：同样附带 UTF-8 编码
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        // JavaScript 脚本文件：附带 UTF-8 编码
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        // PNG 图片：二进制类型，无需字符编码
        if (filePath.endsWith(".png")) return "image/png";
        // JPG 图片：二进制类型，无需字符编码
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        // 兜底：未知类型统一返回通用二进制流类型，浏览器会触发下载而不是渲染
        return "application/octet-stream";
    }
}
