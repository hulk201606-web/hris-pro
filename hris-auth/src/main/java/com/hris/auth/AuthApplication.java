package com.hris.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AuthApplication 服务启动类。
 * scanBasePackages 指向 com.hris 根包，以同时扫描 hris-common 公共组件与服务自身。
 */
@SpringBootApplication(scanBasePackages = "com.hris")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
