package com.hris.recruit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历文本规则抽取器（RCS-F-007）。
 *
 * <p>诚实约束：本项目无 OCR/NLP 引擎且不依赖外部 API，MVP 为纯规则解析
 * （正则 + 启发式），parse_status 一律 RULE_LIMITED，结果需人工核对。</p>
 */
public final class ResumeTextExtractor {

    /** 标准 RFC 5322 简化式：local@domain.tld（不含引号/注释等冷僻形态）。 */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 大陆手机号：1 开头第二位 3-9 的 11 位数字，前后不能是数字。 */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 姓名启发：文本开头 30 字符内首个 2-4 个连续中文字符。 */
    private static final Pattern NAME_HINT = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}");
    private static final int NAME_SCAN_LIMIT = 30;

    /** 段落标题行：教育/工作/项目相关标题（整行仅标题与可选冒号）。 */
    private static final Pattern SECTION_EDUCATION =
            Pattern.compile("^\\s*(教育(经历|背景)?|教育培训)\\s*[:：]?\\s*$");
    private static final Pattern SECTION_WORK =
            Pattern.compile("^\\s*(工作(经历|经验)?|职业(经历|发展))\\s*[:：]?\\s*$");
    private static final Pattern SECTION_PROJECT =
            Pattern.compile("^\\s*(项目(经历|经验)?|项目实践)\\s*[:：]?\\s*$");
    private static final Pattern SECTION_ANY =
            Pattern.compile("^\\s*(教育(经历|背景)?|教育培训|工作(经历|经验)?|职业(经历|发展)"
                    + "|项目(经历|经验)?|项目实践|技能|技能特长|自我评价|证书)\\s*[:：]?\\s*$");

    private ResumeTextExtractor() {
    }

    /** 抽取结果载体：parse_status 由调用方统一标 RULE_LIMITED。 */
    public record ExtractResult(
            String name, String mobile, String email,
            List<String> educationLines, List<String> experienceLines) {
    }

    /**
     * 对简历纯文本做规则抽取。
     *
     * @param text 简历纯文本（调用方提供，简历以文件引用存储）
     * @return 抽取结果；各字段可能为 null（未抽到）
     */
    public static ExtractResult extract(String text) {
        String name = extractName(text);
        String mobile = extractFirst(MOBILE, text);
        String email = extractFirst(EMAIL, text);
        List<String> education = new ArrayList<>();
        List<String> experience = new ArrayList<>();
        splitSections(text, education, experience);
        return new ExtractResult(name, mobile, email,
                education.isEmpty() ? List.of() : education,
                experience.isEmpty() ? List.of() : experience);
    }

    /** 邮箱抽取：首个 RFC 简化式匹配；无匹配返回 null。 */
    public static String extractEmail(String text) {
        return extractFirst(EMAIL, text);
    }

    /** 手机号抽取：首个大陆 11 位匹配；无匹配返回 null。 */
    public static String extractMobile(String text) {
        return extractFirst(MOBILE, text);
    }

    /**
     * 姓名启发式：取文本开头 30 字符内首个 2-4 连续中文字符。
     * 仅启发、不可靠（如「个人简历」这类标题词也会命中），parse_status 需标 RULE_LIMITED 提示人工核对。
     */
    public static String extractName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String head = text.strip().substring(0, Math.min(NAME_SCAN_LIMIT, text.strip().length()));
        Matcher m = NAME_HINT.matcher(head);
        return m.find() ? m.group() : null;
    }

    /**
     * 按行关键词分段：标题行（教育/工作/项目）切分全文。
     * 教育行进 education，工作/项目行合并进 experience；小写标题行之间其余行忽略。
     */
    public static void splitSections(String text, List<String> education, List<String> experience) {
        if (text == null || text.isBlank()) {
            return;
        }
        String current = null;
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (SECTION_EDUCATION.matcher(line).matches()) {
                current = "EDU";
                continue;
            }
            if (SECTION_WORK.matcher(line).matches() || SECTION_PROJECT.matcher(line).matches()) {
                current = "EXP";
                continue;
            }
            if (SECTION_ANY.matcher(line).matches()) {
                current = null;
                continue;
            }
            if ("EDU".equals(current)) {
                education.add(line);
            } else if ("EXP".equals(current)) {
                experience.add(line);
            }
        }
    }

    private static String extractFirst(Pattern p, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }
}
