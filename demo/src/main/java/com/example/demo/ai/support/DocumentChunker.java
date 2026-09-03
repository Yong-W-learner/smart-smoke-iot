package com.example.demo.ai.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识文档切分：先按 Markdown 标题切成节，节内按行滑窗聚合到 400~800 字，
 * 相邻块保留约 100 字重叠，避免语义在边界被切断。
 */
public final class DocumentChunker {

    public static final int TARGET_MIN = 400;
    public static final int TARGET_MAX = 800;
    public static final int OVERLAP = 100;

    private DocumentChunker() {
    }

    public static class Chunk {
        public final int index;
        public final String section;
        public final String content;

        public Chunk(int index, String section, String content) {
            this.index = index;
            this.section = section;
            this.content = content;
        }
    }

    public static List<Chunk> chunk(String documentText) {
        String normalized = HashSupport.normalize(documentText);
        List<String[]> sections = splitSections(normalized);
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (String[] section : sections) {
            String title = section[0];
            String body = section[1].trim();
            if (body.isEmpty()) continue;
            for (String piece : window(body)) {
                chunks.add(new Chunk(index++, title, piece));
            }
        }
        return chunks;
    }

    /** 行级滑窗：每块尽量落在 TARGET_MIN~TARGET_MAX 字之间，块间重叠约 OVERLAP 字。 */
    private static List<String> window(String text) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int remaining = text.length() - start;
            if (remaining <= TARGET_MAX) {
                pieces.add(text.substring(start).trim());
                break;
            }
            int hardEnd = start + TARGET_MAX;
            // 优先在 [start+TARGET_MIN, hardEnd] 内找最后一个换行作为块边界。
            int end = -1;
            for (int i = hardEnd; i >= start + TARGET_MIN; i--) {
                if (text.charAt(i) == '\n') { end = i + 1; break; }
            }
            if (end < 0) {
                int space = text.lastIndexOf(' ', hardEnd);
                end = space > start + TARGET_MIN ? space + 1 : hardEnd;
            }
            pieces.add(text.substring(start, end).trim());
            int next = end - OVERLAP;
            start = next > start ? next : end;
        }
        return pieces;
    }

    /** 返回 [标题路径, 正文]；整文档无标题时只有一节。 */
    private static List<String[]> splitSections(String text) {
        List<String[]> sections = new ArrayList<>();
        String heading = "";
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (isHeading(trimmed)) {
                if (body.length() > 0) {
                    sections.add(new String[]{heading, body.toString()});
                    body.setLength(0);
                }
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                String title = trimmed.substring(level).trim();
                heading = heading.isEmpty() ? title : heading + " > " + title;
                body.append(line).append('\n');
            } else {
                body.append(line).append('\n');
            }
        }
        if (body.length() > 0) sections.add(new String[]{heading, body.toString()});
        return sections;
    }

    private static boolean isHeading(String trimmed) {
        if (trimmed.isEmpty() || trimmed.charAt(0) != '#') return false;
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
        return level <= 6 && trimmed.length() > level && trimmed.charAt(level) == ' ';
    }
}
