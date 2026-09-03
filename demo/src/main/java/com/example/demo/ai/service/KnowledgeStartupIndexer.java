package com.example.demo.ai.service;

import com.example.demo.ai.config.ForestAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 首次启动时若知识库从未成功索引过，则延迟提交一次自动索引（幂等，哈希未变会跳过）。
 * 模型或 Qdrant 未就绪只会让文档停在 failed/pending，管理员仍可在页面手动重建，
 * 该任务绝不阻塞应用启动。
 */
@Component
public class KnowledgeStartupIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStartupIndexer.class);

    private final ForestAiProperties properties;
    private final KnowledgeIngestionService ingestionService;
    private final JdbcTemplate jdbc;

    public KnowledgeStartupIndexer(ForestAiProperties properties, KnowledgeIngestionService ingestionService, JdbcTemplate jdbc) {
        this.properties = properties;
        this.ingestionService = ingestionService;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) return;
        Thread worker = new Thread(this::indexOnceWhenEmpty, "knowledge-startup-indexer");
        worker.setDaemon(true);
        worker.start();
    }

    private void indexOnceWhenEmpty() {
        try {
            // 等 Ollama/Qdrant 容器与模型拉取任务先起步，避免刚启动就打空。
            Thread.sleep(45_000L);
            Integer indexed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_knowledge_document WHERE status='indexed'", Integer.class);
            if (indexed != null && indexed > 0) return;
            log.info("知识库尚未成功索引，触发首次自动索引（模型/向量库不可用时会记录为待处理，不影响业务）");
            ingestionService.reindexNow(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("首次自动索引未完成（可稍后由管理员手动重建）：{}", e.getMessage());
        }
    }
}
