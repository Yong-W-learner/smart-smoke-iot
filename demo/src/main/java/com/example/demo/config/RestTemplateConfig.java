package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 用于调用本地 Python YOLO 识别服务的 HTTP 客户端。
 * 设置较短连接超时（服务没启动时快速失败）和较长读超时（CPU 推理需要几秒）。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);   // 3秒连不上就当服务不可用
        factory.setReadTimeout(30000);     // 30秒内返回结果即可
        return new RestTemplate(factory);
    }
}
