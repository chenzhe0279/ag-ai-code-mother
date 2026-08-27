package com.ag.agaicodemother.controller;


import com.ag.agaicodemother.common.BaseResponse;
import com.ag.agaicodemother.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck(){
        return ResultUtils.success("OK");
    }
}
