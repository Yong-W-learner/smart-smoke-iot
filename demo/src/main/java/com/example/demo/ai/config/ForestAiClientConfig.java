package com.example.demo.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * AI 专用 HTTP 客户端：与原 YOLO/巡护总结使用的默认 RestTemplate 隔离，
 * 连接均设短超时快速失败，读超时区分长对话、批量嵌入与瞬时探活。
 */
@Configuration
public class ForestAiClientConfig {

    /** 对话生成：等待 qwen3:4b 完成（GPU 冷启动可能需要几十秒）。 */
    @Bean("aiChatRestTemplate")
    public RestTemplate aiChatRestTemplate(ForestAiProperties properties) {
        return build(3000, properties.getTimeoutSeconds() * 1000);
    }

    /** 嵌入与 Qdrant 读写：模型首载较慢，给 60s。 */
    @Bean("aiDataRestTemplate")
    public RestTemplate aiDataRestTemplate() {
        return build(3000, 60000);
    }

    /** 健康检查探活：失败要快，不能拖住 health 接口。 */
    @Bean("aiProbeRestTemplate")
    public RestTemplate aiProbeRestTemplate() {
        return build(1500, 3000);
    }

    private RestTemplate build(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}
