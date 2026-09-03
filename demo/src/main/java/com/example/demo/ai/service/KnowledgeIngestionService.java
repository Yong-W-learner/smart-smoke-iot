package com.example.demo.ai.service;

import com.example.demo.ai.client.OllamaClient;
import com.example.demo.ai.client.QdrantClient;
import com.example.demo.ai.config.ForestAiProperties;
import com.example.demo.ai.support.DocumentChunker;
import com.example.demo.ai.support.HashSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库入库：扫描 knowledge/ 与管理员上传目录，按标题/自然段切分，
 * 用本地 qwen3-embedding:0.6b 生成向量写入 Qdrant。内容哈希未变化的文档跳过，
 * 修改/删除的文档先清理旧向量。任何失败只影响知识库状态，不影响业务。
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final long MAX_TEXT_FILE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("md", "txt", "pdf"));
    /** 文件名/路径包含这些片段的文档永不索引（密钥、日志、构建产物、个人目录等）。 */
    private static final List<String> DENY_PATH_PATTERNS = Arrays.asList(
            ".env", "secret", "password", "passwd", "token", "apikey", "api-key", "credential",
            "node_modules", "/target/", "/dist/", "/.git/", "/logs/", ".log", ".key", ".pem",
            "application-prod", "id_rsa", "/.ssh/", "/private/");
    private static final Set<String> ALLOWED_UPLOAD_SUFFIX = new HashSet<>(Arrays.asList(".md", ".txt", ".pdf"));

    private final ForestAiProperties properties;
    private final OllamaClient ollamaClient;
    private final QdrantClient qdrantClient;
    private final JdbcTemplate jdbc;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "knowledge-indexer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String lastError;

    public KnowledgeIngestionService(ForestAiProperties properties, OllamaClient ollamaClient,
                                     QdrantClient qdrantClient, JdbcTemplate jdbc) {
        this.properties = properties;
        this.ollamaClient = ollamaClient;
        this.qdrantClient = qdrantClient;
        this.jdbc = jdbc;
    }

    // ---------- 扫描与判定 ----------

    static boolean isAllowedPath(String relativePath) {
        String value = ("/" + relativePath).toLowerCase(Locale.ROOT);
        for (String denied : DENY_PATH_PATTERNS) {
            if (value.contains(denied)) return false;
        }
        String name = value.substring(value.lastIndexOf('/') + 1);
        if (name.isEmpty()) return false;
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            // 无扩展名文件仅允许 README 类项目说明。
            return name.startsWith("readme");
        }
        return ALLOWED_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /** 收集待索引文件：relativePath -> 磁盘文件。 */
    Map<String, File> discoverFiles() {
        Map<String, File> found = new LinkedHashMap<>();
        collectRoot(new File(properties.getKnowledgeDir()), found, "");
        collectRoot(new File(properties.getUploadDir()), found, "uploads");
        return found;
    }

    private void collectRoot(File root, Map<String, File> found, String namespace) {
        File canonical = canonical(root);
        if (canonical == null || !canonical.isDirectory()) return;
        List<File> queue = new ArrayList<>();
        queue.add(canonical);
        while (!queue.isEmpty()) {
            File directory = queue.remove(queue.size() - 1);
            File[] children = directory.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    String name = child.getName().toLowerCase(Locale.ROOT);
                    if (name.equals(".git") || name.equals("node_modules") || name.equals("target")
                            || name.equals("dist") || name.equals("logs")) continue;
                    queue.add(child);
                    continue;
                }
                String relative = HashSupport.relativize(canonical, child);
                String key = namespace.isEmpty() ? relative : namespace + "/" + relative;
                if (!isAllowedPath(key)) continue;
                if (child.length() > MAX_TEXT_FILE_BYTES) {
                    log.info("跳过超大知识文件：{}", key);
                    continue;
                }
                found.put(key, child);
            }
        }
    }

    private File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
    }

    // ---------- 文本提取 ----------

    static String extractText(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return extractPdf(file);
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String extractPdf(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            if (document.isEncrypted()) throw new IOException("加密 PDF 不支持索引");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    // ---------- 入库任务 ----------

    /** 提交一次重建/增量索引任务；AI 组件不可用时返回提示信息但不抛异常。 */
    public String requestReindex(String username, boolean force) {
        if (!properties.isEnabled()) return "AI 功能已通过 FOREST_AI_ENABLED=false 关闭";
        if (!running.compareAndSet(false, true)) return "索引任务正在进行中，请稍后再试";
        lastError = null;
        executor.submit(() -> {
            try {
                reindexNow(force);
            } catch (Exception e) {
                lastError = brief(e);
                log.warn("知识库索引任务失败：{}", e.getMessage());
            } finally {
                running.set(false);
            }
        });
        return "索引任务已提交（用户 " + username + "），可稍后在文档列表查看进度";
    }

    /** 同步执行完整扫描索引（单元测试与初始化可用）。 */
    public Map<String, Object> reindexNow(boolean force) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int indexed = 0, skipped = 0, failed = 0, removed = 0;
        Map<String, File> files = discoverFiles();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, File> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            seen.add(relativePath);
            File file = entry.getValue();
            try {
                String text = extractText(file);
                String hash = HashSupport.sha256Hex(text);
                Map<String, Object> row = findRow(relativePath);
                if (!force && row != null && hash.equals(row.get("contentHash")) && "indexed".equals(row.get("status"))) {
                    skipped++;
                    continue;
                }
                long documentId = upsertRow(row, file.getName(), relativePath, hash, "indexing");
                indexDocument(documentId, file.getName(), relativePath, text, hash);
                markIndexed(documentId, chunkCountOf(documentId));
                indexed++;
            } catch (Exception e) {
                failed++;
                markFailed(relativePath, brief(e));
                log.warn("知识文档索引失败 {}：{}", relativePath, e.getMessage());
            }
        }
        // 磁盘上已消失的文档：清理向量并标记 removed。
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,relative_path AS relativePath,content_hash AS contentHash,status FROM ai_knowledge_document WHERE status<>'removed'");
        for (Map<String, Object> row : rows) {
            String relativePath = String.valueOf(row.get("relativePath"));
            if (seen.contains(relativePath)) continue;
            try {
                if (qdrantClient.collectionExists()) qdrantClient.deleteByDocument(((Number) row.get("id")).longValue());
            } catch (Exception e) {
                log.warn("清理已删除文档向量失败：{}", e.getMessage());
            }
            jdbc.update("UPDATE ai_knowledge_document SET status='removed',chunk_count=0,update_time=NOW() WHERE id=?", row.get("id"));
            removed++;
        }
        summary.put("found", files.size());
        summary.put("indexed", indexed);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("removed", removed);
        return summary;
    }

    /** 单文档索引：删除旧向量 → 切分 → 嵌入 → 写入 Qdrant → 更新行数。 */
    void indexDocument(long documentId, String filename, String relativePath, String text, String hash) {
        List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(text);
        try {
            if (qdrantClient.collectionExists()) qdrantClient.deleteByDocument(documentId);
        } catch (Exception ignored) {
            // 集合不存在时稍后统一创建
        }
        if (chunks.isEmpty()) {
            jdbc.update("UPDATE ai_knowledge_document SET chunk_count=0 WHERE id=?", documentId);
            return;
        }
        List<String> texts = new ArrayList<>();
        for (DocumentChunker.Chunk chunk : chunks) texts.add(chunk.content);
        List<double[]> vectors = ollamaClient.embed(properties.getEmbeddingModel(), texts);
        qdrantClient.ensureCollection(vectors.get(0).length);
        List<Map<String, Object>> points = new ArrayList<>();
        String updatedAt = java.time.LocalDateTime.now().withNano(0).toString();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunker.Chunk chunk = chunks.get(i);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", documentId);
            payload.put("filename", filename);
            payload.put("relativePath", relativePath);
            payload.put("section", chunk.section);
            payload.put("chunkIndex", chunk.index);
            payload.put("contentHash", hash);
            payload.put("updatedAt", updatedAt);
            payload.put("text", chunk.content);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", UUID.nameUUIDFromBytes((documentId + "#" + i).getBytes(StandardCharsets.UTF_8)).toString());
            List<Double> vector = new ArrayList<>(vectors.get(i).length);
            for (double value : vectors.get(i)) vector.add(value);
            point.put("vector", vector);
            point.put("payload", payload);
            points.add(point);
        }
        qdrantClient.upsert(points);
        jdbc.update("UPDATE ai_knowledge_document SET chunk_count=? WHERE id=?", chunks.size(), documentId);
    }

    private long chunkCountOf(long documentId) {
        Integer count = jdbc.queryForObject("SELECT chunk_count FROM ai_knowledge_document WHERE id=?", Integer.class, documentId);
        return count == null ? 0L : count;
    }

    // ---------- 上传 ----------

    /** 管理员上传：校验类型/大小/文件名，保存到上传目录并登记；随后触发异步索引。 */
    public Map<String, Object> storeUpload(MultipartFile file, String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        String safeName = HashSupport.sanitizeFilename(file.getOriginalFilename());
        if (safeName.isEmpty()) throw new IllegalArgumentException("文件名不合法");
        int dot = safeName.lastIndexOf('.');
        String suffix = dot < 0 ? "" : safeName.substring(dot).toLowerCase(Locale.ROOT);
        if (!ALLOWED_UPLOAD_SUFFIX.contains(suffix)) {
            throw new IllegalArgumentException("仅允许上传 .md、.txt、.pdf 文档");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new IllegalArgumentException("文件大小超过限制（" + properties.getMaxUploadMb() + "MB）");
        }
        File uploadRoot = new File(properties.getUploadDir());
        if (!uploadRoot.isDirectory() && !uploadRoot.mkdirs()) throw new IllegalStateException("无法创建上传目录");
        byte[] bytes;
        try (InputStream in = file.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > properties.getMaxUploadBytes()) throw new IllegalArgumentException("文件大小超过限制");
                out.write(buffer, 0, read);
            }
            bytes = out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败");
        }
        String hash = HashSupport.sha256Hex(bytes);
        String storedName = hash.substring(0, 16) + suffix;
        File target = HashSupport.resolveWithin(uploadRoot, storedName);
        if (target == null) throw new IllegalArgumentException("非法存储路径");
        try {
            Files.write(target.toPath(), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("保存上传文件失败");
        }
        String relativePath = "uploads/" + storedName;
        Map<String, Object> existing = findRow(relativePath);
        long documentId = upsertRow(existing, safeName, relativePath, hash, "pending");
        result.put("documentId", documentId);
        result.put("filename", safeName);
        result.put("relativePath", relativePath);
        result.put("message", requestReindex(username, false));
        return result;
    }

    // ---------- 查询 ----------

    public List<Map<String, Object>> documents() {
        return jdbc.queryForList("SELECT id,filename AS filename,relative_path AS relativePath,content_hash AS contentHash," +
                "status,chunk_count AS chunkCount,last_error AS lastError," +
                "DATE_FORMAT(indexed_at,'%Y-%m-%d %H:%i:%s') AS indexedAt," +
                "DATE_FORMAT(update_time,'%Y-%m-%d %H:%i:%s') AS updatedAt FROM ai_knowledge_document" +
                " ORDER BY update_time DESC LIMIT 500");
    }

    public Map<String, Object> statusSummary() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running.get());
        status.put("lastError", lastError);
        return status;
    }

    // ---------- DB helpers ----------

    private Map<String, Object> findRow(String relativePath) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,content_hash AS contentHash,status FROM ai_knowledge_document WHERE relative_path=?", relativePath);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long upsertRow(Map<String, Object> existing, String filename, String relativePath, String hash, String status) {
        if (existing != null) {
            long id = ((Number) existing.get("id")).longValue();
            jdbc.update("UPDATE ai_knowledge_document SET filename=?,content_hash=?,status=?,last_error=NULL,update_time=NOW() WHERE id=?",
                    filename, hash, status, id);
            return id;
        }
        jdbc.update("INSERT INTO ai_knowledge_document(filename,relative_path,content_hash,status) VALUES(?,?,?,?)",
                filename, relativePath, hash, status);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private void markIndexed(long documentId, long chunkCount) {
        jdbc.update("UPDATE ai_knowledge_document SET status='indexed',chunk_count=?,indexed_at=NOW(),last_error=NULL,update_time=NOW() WHERE id=?",
                chunkCount, documentId);
    }

    private void markFailed(String relativePath, String error) {
        jdbc.update("UPDATE ai_knowledge_document SET status='failed',last_error=?,update_time=NOW() WHERE relative_path=?", error, relativePath);
    }

    static String brief(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        if (message.length() > 480) message = message.substring(0, 480);
        return message;
    }
}
