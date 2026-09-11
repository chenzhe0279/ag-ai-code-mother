package com.ag.agaicodemother.core;


import cn.hutool.json.JSONUtil;
import com.ag.agaicodemother.ai.AiCodeGeneratorService;
import com.ag.agaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;
import com.ag.agaicodemother.ai.model.message.AiResponseMessage;
import com.ag.agaicodemother.ai.model.message.ToolExecutedMessage;
import com.ag.agaicodemother.ai.model.message.ToolRequestMessage;
import com.ag.agaicodemother.core.parser.CodeParserExecutor;
import com.ag.agaicodemother.core.saver.CodeFileSaverExecutor;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecution;
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
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;


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
        //根据appID获取对应的 AI 实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, version);
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
        //根据appID获取对应的 AI 实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, version);
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
            case VUE_PROJECT -> {
                // 获取多文件代码流
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId,userMessage);
                // 交给通用流式处理方法，带上版本号
                yield processTokenStream(tokenStream);
            }
            default -> {
                // 不支持的生成类型
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream
                // 注册流式部分响应回调：模型每生成一个文本片段就会触发一次
                .onPartialResponse((String partialResponse) -> {
                    // 将本次返回的文本片段封装为统一的 AI 响应消息对象
                    AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                    // 将消息对象转换为 JSON 字符串，再通过 sink 推送给下游订阅者
                    sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                })
                // 注册工具调用请求回调：模型请求调用某个工具时触发
                // index 为工具调用序号，toolExecutionRequest 是工具调用的请求信息（工具名、参数等）
                .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                    // 将工具调用请求包装为工具请求消息，便于前端按事件类型解析
                    ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                    // 将工具请求消息序列化为 JSON 字符串并推送出去
                    sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                })
                // 注册工具执行完成回调：模型调用的工具执行完毕后触发
                // toolExecution 中携带工具实际执行结果，可直接序列化后通知前端
                .onToolExecuted((ToolExecution toolExecution) -> {
                    // 包装工具执行结果，统一消息结构，便于前端识别工具执行完成事件
                    ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                    // 将执行结果序列化为 JSON 字符串，通过 sink 发送给下游
                    sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                })
                // 注册完整响应完成回调：整个 AI 响应生命周期正常结束时触发一次
                .onCompleteResponse((ChatResponse response) -> {
                    // response 为完整聊天响应对象，此处无需再向下游传内容
                    // 主动调用 complete 结束 Flux，通知前端流式响应已正常完成
                    sink.complete();
                })
                // 注册错误回调：流式输出、工具调用等任意环节出错时触发
                .onError((Throwable error) -> {
                    // 打印原始异常堆栈，方便服务端开发人员定位具体问题
                    error.printStackTrace();
                    // 将异常传递给 sink，让当前 Flux 以错误信号结束，外层可统一处理失败状态
                    sink.error(error);
                })
                // 开始订阅并触发整个 TokenStream：只有调用 start 后上面注册的回调才会实际执行
                .start();
        });
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
                        // 保存失败让流以错误结束：外层 doOnError 会把状态标为 failed，
                        // 前端也会提示生成中断，避免"状态已成功但磁盘无文件"的假象
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码保存失败：" + e.getMessage());
                    }
                });
    }
}
