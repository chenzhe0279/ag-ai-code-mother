package com.ag.agaicodemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ag.agaicodemother.mapper")
public class AgAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgAiCodeMotherApplication.class, args);
    }

}
