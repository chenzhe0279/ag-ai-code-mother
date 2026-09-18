package com.ag.agaicodemother.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.ag.agaicodemother.langgraph4j.enums.ImageCategoryEnum;
import com.ag.agaicodemother.langgraph4j.model.ImageResource;
import com.ag.agaicodemother.manager.CosManager;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Resource
    private CosManager cosManager;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        // 用于收集最终生成并成功上传到 COS 的 Logo 资源
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            // 构建 DashScope 文生图请求参数
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();
            // 调用文生图接口（同步阻塞）
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);
            // 校验返回结果是否有效
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                // 逐张处理返回的图片
                for (Map<String, String> imageResult : results) {
                    // DashScope 返回的是远程临时 URL（约 24 小时过期），需要转存到 COS 变成永久地址
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isBlank(imageUrl)) {
                        continue;
                    }
                    // 将远程图片下载并上传到 COS，返回永久访问地址；失败则返回 null
                    String cosUrl = downloadAndUploadToCos(imageUrl);
                    // 仅在上传成功时才加入结果集
                    if (StrUtil.isNotBlank(cosUrl)) {
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(cosUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }

    /**
     * 下载远程图片并上传到 COS
     *
     * @param imageUrl DashScope 返回的远程临时图片地址
     * @return COS 永久访问地址，失败返回 null
     */
    private String downloadAndUploadToCos(String imageUrl) {
        // 创建本地临时文件用于承接下载的图片，扩展名根据远程 URL 推断
        File tempFile = FileUtil.createTempFile("logo_", "." + imageUrl, true);
        try {
            // 下载远程图片到本地临时文件
            HttpUtil.downloadFile(imageUrl, tempFile);
            // 下载失败（文件为空）时直接返回 null
            if (!tempFile.exists() || tempFile.length() == 0) {
                log.error("下载远程 Logo 图片失败: {}", imageUrl);
                return null;
            }
            // 构建 COS 对象键，格式：/logo/{随机串}/{唯一文件名}
            String keyName = String.format("/mermaid/%s/%s",
                    RandomUtil.randomString(5),
                    tempFile.getName());
            // 上传本地临时文件到 COS，返回永久访问地址
            return cosManager.uploadFile(keyName, tempFile);
        } catch (Exception e) {
            log.error("上传 Logo 到 COS 失败: {}", e.getMessage(), e);
            return null;
        } finally {
            // 无论成功失败都清理本地临时文件，避免磁盘堆积
            FileUtil.del(tempFile);
        }
    }

    /**
     * 从远程图片 URL 中解析文件扩展名，无法解析时默认返回 png
     *
     * @param imageUrl 远程图片地址（可能带有查询参数）
     * @return 文件扩展名（不含点）
     */
    /*private String resolveExtension(String imageUrl) {
        // 去掉 URL 中的查询参数，只保留路径部分
        String path = StrUtil.subBefore(imageUrl, "?", false);
        // 截取最后一个点之后的内容作为扩展名
        String ext = StrUtil.subAfter(path, ".", true);
        // 扩展名合法性校验，非法则默认 png
        if (StrUtil.isBlank(ext) || ext.length() > 5) {
            return "png";
        }
        return ext;
    }*/
}
