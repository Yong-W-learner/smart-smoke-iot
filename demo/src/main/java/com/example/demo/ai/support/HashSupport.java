package com.example.demo.ai.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * 内容哈希与文件安全工具：SHA-256 归一化哈希、文件名清洗、路径穿越防护。
 */
public final class HashSupport {

    /** 仅允许中文、字母、数字、点、下划线、短横与空格组合出的安全文件名片段。 */
    private static final Pattern SAFE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9._\\- ]{1,120}$");

    private HashSupport() {
    }

    public static String sha256Hex(String text) {
        return sha256Hex(normalize(text).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 归一化：统一换行、去行尾空白、压缩连续空行，避免无关排版造成重复索引。 */
    public static String normalize(String text) {
        if (text == null) return "";
        String value = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = value.split("\n", -1);
        StringBuilder sb = new StringBuilder(value.length());
        int blankRun = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blankRun++;
                if (blankRun <= 1) sb.append('\n');
                continue;
            }
            blankRun = 0;
            sb.append(trimmed).append('\n');
        }
        return sb.toString();
    }

    /** 去掉路径分隔符与控制字符，保留可辨识的文件名。 */
    public static String sanitizeFilename(String name) {
        if (name == null) return "";
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[\\u0000-\\u001f\\u007f]", "").replace("%", "").trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) return "";
        if (base.length() > 120) base = base.substring(0, 120);
        return SAFE_NAME.matcher(base).matches() ? base : "";
    }

    /**
     * 安全解析：目标文件规范化后必须仍位于 baseDir 内（防 ../../ 穿越），
     * 且不允许符号链接跳出。越界返回 null。
     */
    public static File resolveWithin(File baseDir, String relativePath) {
        if (relativePath == null) return null;
        String value = relativePath.trim();
        if (value.isEmpty() || value.startsWith("/") || value.contains("\0")) return null;
        value = value.replace('\\', '/');
        for (String segment : value.split("/")) {
            if (segment.equals("..")) return null;
        }
        try {
            File base = baseDir.getCanonicalFile();
            File target = new File(base, value).getCanonicalFile();
            return target.getPath().startsWith(base.getPath() + File.separator) || target.equals(base) ? target : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** 相对 baseDir 的规范化路径（正斜杠）。 */
    public static String relativize(File baseDir, File file) {
        try {
            String base = baseDir.getCanonicalFile().getPath();
            String full = file.getCanonicalFile().getPath();
            if (!full.startsWith(base)) return file.getName();
            String rel = full.substring(base.length());
            if (rel.startsWith(java.io.File.separator)) rel = rel.substring(1);
            return rel.replace(java.io.File.separatorChar, '/');
        } catch (IOException e) {
            return file.getName();
        }
    }
}
