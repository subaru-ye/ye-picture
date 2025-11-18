package com.ye.yepicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication
@EnableAsync
@MapperScan("com.ye.yepicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class YePictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YePictureBackendApplication.class, args);
    }

}
