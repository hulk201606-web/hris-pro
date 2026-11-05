package com.hris.employee.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 租户级工号模板（EMP-F-002）。支持占位符：
 *
 * <ul>
 *   <li>{@code {PREFIX}} 工号前缀（请求缺省时回退 e）</li>
 *   <li>{@code {YYYY}} 四位年度 / {@code {YY}} 两位年度</li>
 *   <li>{@code {SEQ}} 序号 / {@code {SEQ:4}} 左补零到 4 位</li>
 * </ul>
 *
 * <p>最小实现：未知占位符视为模板非法（{@link #isSupported} 返回 false），由调用方
 * 回退雪花工号，避免把 {@code {FOO}} 原样写进工号。模板必须含 SEQ 占位符，否则
 * 同一租户会生成重复工号。</p>
 */
public final class EmpNoPattern {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Z]+)(?::(\\d+))?}");
    private static final int MAX_SEQ_WIDTH = 12;
    private static final int MAX_EMP_NO_LENGTH = 32;

    private EmpNoPattern() {
    }

    /** 模板是否可用：占位符都在白名单内、含 SEQ、渲染长度不超过工号列宽。 */
    public static boolean isSupported(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        boolean hasSeq = false;
        Matcher m = PLACEHOLDER.matcher(pattern.trim());
        while (m.find()) {
            String token = m.group(1);
            switch (token) {
                case "PREFIX", "YYYY", "YY" -> {
                }
                case "SEQ" -> hasSeq = true;
                default -> {
                    return false;
                }
            }
        }
        String residue = PLACEHOLDER.matcher(pattern.trim()).replaceAll("");
        if (residue.contains("{") || residue.contains("}")) {
            // 形如 {SEQ:abc} / 未闭合花括号：正则未匹配，原样输出会污染工号
            return false;
        }
        return hasSeq && render(pattern, "e", 2026, 1).length() <= MAX_EMP_NO_LENGTH;
    }

    /**
     * 渲染工号。
     *
     * @param pattern 模板（调用前应通过 {@link #isSupported}）
     * @param prefix  前缀（null/空白回退 e，统一 trim+lower，符合 docs/12 标识符规范）
     * @param year    年度
     * @param seq     序号（从 1 开始）
     */
    public static String render(String pattern, String prefix, int year, int seq) {
        String p = prefix == null || prefix.isBlank() ? "e" : prefix.trim().toLowerCase();
        Matcher m = PLACEHOLDER.matcher(pattern.trim());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            String replacement = switch (token) {
                case "PREFIX" -> p;
                case "YYYY" -> String.format("%04d", year);
                case "YY" -> String.format("%02d", year % 100);
                case "SEQ" -> pad(seq, m.group(2));
                default -> m.group();
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        String out = sb.toString();
        return out.length() <= MAX_EMP_NO_LENGTH ? out : out.substring(0, MAX_EMP_NO_LENGTH);
    }

    /** 序号占位符的解析值：无此占位符返回 null（调用方据此决定是否用序号段）。 */
    public static Integer seqWidth(String pattern) {
        if (pattern == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(pattern);
        while (m.find()) {
            if ("SEQ".equals(m.group(1))) {
                return m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            }
        }
        return null;
    }

    private static String pad(int seq, String widthText) {
        int width = widthText == null ? 1 : Integer.parseInt(widthText);
        width = Math.max(1, Math.min(MAX_SEQ_WIDTH, width));
        return String.format("%0" + width + "d", Math.max(0, seq));
    }
}
