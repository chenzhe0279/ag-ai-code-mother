package com.ag.agaicodemother.core;


import com.ag.agaicodemother.ai.AiCodeGeneratorService;
import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;
import com.ag.agaicodemother.core.parser.CodeParserExecutor;
import com.ag.agaicodemother.core.saver.CodeFileSaverExecutor;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;


    /**
     * 统一入口：根据类型生成并保存代码（非流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId     应用ID
     * @param version   版本号（版本化改造新增参数：代码保存到 v{version} 目录）
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage , CodeGenTypeEnum codeGenTypeEnum , Long appId , Integer version){
        // 生成类型为空直接抛异常，避免后续 switch 匹配失败
        if(codeGenTypeEnum == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum){
            case HTML -> {
                // 调用 AI 服务生成 HTML 单文件代码
                HtmlCodeResult result = aiCodeGeneratorService.generateHTMLCode(userMessage);
                // 保存到 v{version} 版本目录
                yield  CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML , appId , version);
            }
            case MULTI_FILE -> {
                // 调用 AI 服务生成多文件代码（HTML+CSS+JS）
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                // 保存到 v{version} 版本目录
                yield  CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE , appId , version);
            }
            default -> {
                // 不支持的生成类型
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId     应用ID
     * @param version   版本号（版本化改造新增参数：代码保存到 v{version} 目录）
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum , Long appId , Integer version) {
        // 生成类型为空直接抛异常
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // 获取 HTML 代码流
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                // 交给通用流式处理方法，带上版本号
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId, version);
            }
            case MULTI_FILE -> {
                // 获取多文件代码流
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                // 交给通用流式处理方法，带上版本号
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId, version);
            }
            default -> {
                // 不支持的生成类型
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId     应用ID
     * @param version   版本号（流式结束后保存到 v{version} 目录）
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream , CodeGenTypeEnum codeGenType , Long appId , Integer version) {
        //创建一个StringBuilder对象，用于拼接流失输出的信息并保存
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(code -> {
                    //实时收集结果数据
                    codeBuilder.append(code);
                }).doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try {
                        String completeCode = codeBuilder.toString();
                        //使用执行器解析代码
                        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                        //使用执行器保存代码文件到 v{version} 版本目录
                        File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType , appId , version);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    }catch (Exception e) {
                        // 保存失败只记录日志不中断流。
                        // 注意：版本号已在生成前预留，失败会导致该版本号"跳空"（无对应目录），
                        // 这是预留制方案的已知取舍，部署/列表/静态访问接口均有存在性校验兜底。
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }
}
