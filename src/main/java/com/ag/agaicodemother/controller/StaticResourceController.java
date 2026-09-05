package com.ag.agaicodemother.controller;

import com.ag.agaicodemother.constant.AppConstant;
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
public class StaticResourceController {

    // 生成代码的根目录（用于预览浏览）
    // System.getProperty("user.dir") 获取 JVM 启动时的工作目录（即项目根目录）
    // 与 CodeFileSaver 中 FILE_SAVE_ROOT_DIR 保持一致，保证"保存"和"读取"使用同一目录
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     *
     * @param deployKey 部署唯一标识（对应 tmp/code_output 下的子目录名，如 multi_file_453433342301114368）
     * @param request   HTTP 请求对象，用于获取完整匹配路径和请求 URI
     * @return 文件资源响应；目录无斜杠时返回 301 重定向
     */
    // 映射 GET 请求；{deployKey} 为路径变量，/** 表示匹配该目录下的任意层级路径（包括多层子目录）
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取当前请求在该处理器映射下匹配到的完整路径（如 /static/multi_file_xxx/css/style.css）
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            // 截掉前缀 "/static/{deployKey}"，得到 deployKey 目录下的相对资源路径（如 /css/style.css）
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果相对路径为空，说明用户访问的是目录本身且 URL 末尾没有斜杠（如 /static/multi_file_xxx）
            // 不带斜杠会导致浏览器把当前路径当"文件"，页面内的相对引用（./script.js）会解析错误，所以需要重定向补上斜杠
            if (resourcePath.isEmpty()) {
                // 创建响应头对象，用于承载重定向目标
                HttpHeaders headers = new HttpHeaders();
                // 设置 Location 头：指向原 URI 加一个斜杠，浏览器收到后会自动跳转
                headers.add("Location", request.getRequestURI() + "/");
                // 返回 301 永久重定向响应（只含响应头，无响应体）
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 如果访问的是目录根路径（如 /static/multi_file_xxx/），默认返回首页
            if (resourcePath.equals("/")) {
                // 将资源路径重置为 index.html，实现"访问目录即打开首页"的静态服务器惯例
                resourcePath = "/index.html";
            }
            // 拼接完整的本地文件路径：根目录 + deployKey 子目录 + 相对资源路径
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            // 用文件路径创建 File 对象（不会真正读取文件，仅表示路径）
            File file = new File(filePath);
            // 检查该文件在本地是否真实存在
            if (!file.exists()) {
                // 文件不存在时返回 404 Not Found（无响应体）
                return ResponseEntity.notFound().build();
            }
            // 把本地文件包装成 Spring 的 Resource 资源对象，作为响应体返回
            Resource resource = new FileSystemResource(file);
            // 返回 200 OK，并携带响应体和 Content-Type 响应头
            return ResponseEntity.ok()
                    // 根据文件扩展名设置对应的 MIME 类型（文本类资源附带 UTF-8 编码声明）
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    // 设置响应体为文件资源
                    .body(resource);
        } catch (Exception e) {
            // 任何未预期异常（路径越界、IO 异常等）统一返回 500，避免把异常堆栈暴露给前端
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
