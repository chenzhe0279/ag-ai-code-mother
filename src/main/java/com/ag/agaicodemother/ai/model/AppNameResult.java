package com.ag.agaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("AI 根据应用初始描述生成的应用名称")
@Data
public class AppNameResult {

    @Description("生成的应用名称")
    private String name;
}