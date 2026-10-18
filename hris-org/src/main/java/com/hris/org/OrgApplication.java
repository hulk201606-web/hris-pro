package com.hris.org;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OrgApplication 服务启动类。
 * scanBasePackages 指向 com.hris 根包，以同时扫描 hris-common 公共组件与服务自身。
 */
@SpringBootApplication(scanBasePackages = "com.hris")
public class OrgApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrgApplication.class, args);
    }
}
