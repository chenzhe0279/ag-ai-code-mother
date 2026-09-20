package com.ag.agaicodemother.config;

import com.ag.agaicodemother.constant.FileConstant;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web MVC 相关配置。
 * <p>
 * 主要用途：把用户上传到本地磁盘的头像目录暴露为可被浏览器直接访问的静态资源，
 * 解决头像上传成功、数据库 URL 已更新，但前端 &lt;img&gt; 请求 404 无法显示的问题。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册静态资源处理器，将 URL 路径 /avatar/** 映射到本地磁盘的头像目录。
     * <p>
     * 注意：由于 server.servlet.context-path=/api，这里注册的 "/avatar/**"
     * 实际对外访问路径为 "/api/avatar/**"，正好与 UserServiceImpl#uploadAvatar
     * 返回并存库的 URL（request.getContextPath() + "/avatar/" + fileName）一致。
     *
     * @param registry 资源处理器注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 构造头像物理目录的绝对路径：{FILE_SAVE_DIR}/avatar
        File avatarDir = new File(FileConstant.FILE_SAVE_DIR, "avatar");
        // 若目录不存在则提前创建，避免首次访问时因目录缺失导致映射无效
        if (!avatarDir.exists()) {
            avatarDir.mkdirs();
        }
        // 转成 Spring 能识别的 file: 协议 URL：统一使用正斜杠并以 "/" 结尾表示目录
        String location = "file:" + avatarDir.getAbsolutePath().replace("\\", "/") + "/";
        // 将 /avatar/** 的请求交给该物理目录下的文件处理
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations(location);
    }
}