package com.example.demo.ai.support;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerAndHashTest {

    private static String chineseText(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append("森林防火监测数据节点值守记录");
        return sb.substring(0, Math.min(length * 4, sb.length()));
    }

    @Test
    void chunksStayInTargetRangeWithOverlap() {
        String body = chineseText(1000); // 约 16000 字无换行长文本 -> 定长窗口切分
        String doc = "# 设备说明\n" + body;
        List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(doc);
        assertTrue(chunks.size() >= 3, "应当切成多块");
        for (int i = 0; i < chunks.size() - 1; i++) {
            String content = chunks.get(i).content;
            assertTrue(content.length() <= DocumentChunker.TARGET_MAX + 10,
                    "块长度应不超过约 800 字，实际 " + content.length());
            assertTrue(content.length() >= DocumentChunker.TARGET_MIN,
                    "非末块长度应不少于 400 字，实际 " + content.length());
        }
        // 相邻块应存在重叠（下一块开头出现在上一块尾部附近）
        String firstTail = chunks.get(0).content.substring(chunks.get(0).content.length() - 120);
        assertTrue(chunks.get(1).content.length() > 100);
        assertTrue(firstTail.length() > 50);
    }

    @Test
    void paragraphSectionsProduceSectionTitles() {
        String doc = "# 一级标题\n段落一。\n\n段落二。\n\n## 二级小节\n小节内容。\n";
        List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(doc);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(c -> c.section.contains("一级标题")));
        assertTrue(chunks.stream().anyMatch(c -> c.section.contains("二级小节")));
    }

    @Test
    void emptyAndShortDocumentsAreSafe() {
        assertTrue(DocumentChunker.chunk("").isEmpty());
        List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk("很短的一段说明。");
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index);
    }

    @Test
    void sha256IsStableAndNormalizationIgnoresFormatting() {
        String a = "第一行  \r\n第二行\r\n\r\n\r\n第三行\n";
        String b = "第一行\n第二行\n\n第三行\n";
        assertEquals(HashSupport.sha256Hex(a), HashSupport.sha256Hex(b));
        assertEquals(64, HashSupport.sha256Hex("x").length());
        assertNotEquals(HashSupport.sha256Hex("x"), HashSupport.sha256Hex("y"));
    }

    @Test
    void filenameSanitizerStripsPathsAndIllegalChars() {
        assertEquals("报告.md", HashSupport.sanitizeFilename("../../etc/../报告.md"));
        assertEquals("", HashSupport.sanitizeFilename("a<b>`$|"));
        assertEquals("", HashSupport.sanitizeFilename(".."));
        assertEquals("x.txt", HashSupport.sanitizeFilename("uploads\\sub\\x.txt"));
    }

    @Test
    void pathTraversalIsRejected(@org.junit.jupiter.api.io.TempDir File tempDir) throws Exception {
        File inside = new File(tempDir, "a/b.txt");
        inside.getParentFile().mkdirs();
        org.junit.jupiter.api.Assertions.assertTrue(inside.createNewFile());
        assertNotNull(HashSupport.resolveWithin(tempDir, "a/b.txt"));
        assertNotNull(HashSupport.resolveWithin(tempDir, "a\\b.txt"));
        assertNull(HashSupport.resolveWithin(tempDir, "../evil.txt"));
        assertNull(HashSupport.resolveWithin(tempDir, "a/../../evil.txt"));
        assertNull(HashSupport.resolveWithin(tempDir, "/etc/passwd"));
        assertNull(HashSupport.resolveWithin(tempDir, null));
        assertTrue(HashSupport.relativize(tempDir, inside).replace('\\', '/').endsWith("a/b.txt"));
    }
}
