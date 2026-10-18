package com.hris.rag;

import com.hris.rag.config.MilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * RagApplication 服务启动类。
 * scanBasePackages 指向 com.hris 根包，以同时扫描 hris-common 公共组件与服务自身。
 */
@SpringBootApplication(scanBasePackages = "com.hris")
@EnableConfigurationProperties(MilvusProperties.class)
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
