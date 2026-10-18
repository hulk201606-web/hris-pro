package com.hris.flow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FlowApplication 服务启动类。
 * scanBasePackages 指向 com.hris 根包，以同时扫描 hris-common 公共组件与服务自身。
 */
@SpringBootApplication(scanBasePackages = "com.hris")
public class FlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowApplication.class, args);
    }
}
