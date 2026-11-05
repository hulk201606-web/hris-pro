package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.dto.FileTextDtos.ExtractTextResponse;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * 文件文本层抽取（批 12 #28，简历 OCR→文本链路的「文本层」环节）。
 *
 * <p>能力边界：只抽取文档<b>自带文本层</b>（PDF 文本对象、DOCX 段落、纯文本等，Tika 标准
 * 解析器）。扫描件/图片型 PDF 无文本层——本服务不接 OCR 引擎，返回
 * {@code hasTextLayer=false} + warning「扫描件需 OCR 引擎，当前未接入」，由调用方决定是否
 * 阻断（recruit 侧降级为原文法报错，不伪造文本）。</p>
 *
 * <p>不支持的类型 / 加密损坏文件：解析异常统一转 {@link ErrorCode#RAG_ERROR}，不返回半成品
 * 文本（静默截断会让下游 LLM 在不完整内容上产出错误结论）。</p>
 */
@Service
public class FileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(FileTextExtractor.class);

    /** 扫描件提示（契约字段，recruit 侧与前端据此提示「需 OCR」）。 */
    public static final String WARN_OCR = "扫描件需 OCR 引擎，当前未接入";
    /** 空文件提示。 */
    public static final String WARN_EMPTY = "文件为空，无可抽取内容";
    /** 其他类型无文本层提示模板。 */
    public static final String WARN_NO_TEXT = "未从文件中提取到文本层（检测类型 %s）";

    private static final String[] PAGE_KEYS = {
            "xmpTPg:NPages", "meta:page-count", "pdf:docinfo:custom:Pages", "Pages"
    };

    private final Tika tika = new Tika();

    /** 抽取 multipart 文件；空文件不报错，返回 warning。 */
    public ExtractTextResponse extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ExtractTextResponse("", null, false, WARN_EMPTY);
        }
        try {
            return extract(file.getBytes(), file.getOriginalFilename());
        } catch (java.io.IOException e) {
            throw new BizException(ErrorCode.RAG_ERROR, "读取上传文件失败：" + e.getMessage());
        }
    }

    /** 抽取内存字节（服务间调用经 platform 下载后走这里）。 */
    public ExtractTextResponse extract(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            return new ExtractTextResponse("", null, false, WARN_EMPTY);
        }
        Metadata metadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        BodyContentHandler handler = new BodyContentHandler(-1);
        try {
            new AutoDetectParser().parse(new ByteArrayInputStream(content), handler, metadata,
                    new ParseContext());
        } catch (Exception e) {
            log.warn("文件解析失败 file={} size={}: {}", fileName, content.length, e.getMessage());
            throw new BizException(ErrorCode.RAG_ERROR, "文件解析失败：" + e.getMessage());
        }
        String text = normalize(handler.toString());
        Integer pages = pages(metadata);
        if (!text.isEmpty()) {
            return new ExtractTextResponse(text, pages, true, null);
        }
        return new ExtractTextResponse("", pages, false, warningFor(detect(content, fileName)));
    }

    /** 空白归一：统一换行、压缩连续空行，去掉解析器常见的 \\u0000 与行尾空白。 */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastBlank = false;
        for (String line : s.split("\n", -1)) {
            String trimmed = line.stripTrailing();
            boolean blank = trimmed.isBlank();
            if (blank && lastBlank) {
                continue;
            }
            sb.append(blank ? "" : trimmed).append('\n');
            lastBlank = blank;
        }
        return sb.toString().trim();
    }

    /** 无文本层时的提示：PDF/图片型才提示 OCR，其他类型给出检测类型。 */
    private static String warningFor(String mediaType) {
        String type = mediaType == null ? "" : mediaType.toLowerCase();
        if (type.contains("pdf") || type.startsWith("image/")) {
            return WARN_OCR;
        }
        return String.format(WARN_NO_TEXT, type.isEmpty() ? "unknown" : type);
    }

    /** 检测媒体类型：扩展名 + 魔数（Tika.detect 对未知扩展名回退二进制探测）。 */
    private String detect(byte[] content, String fileName) {
        try {
            return tika.detect(content, fileName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Integer pages(Metadata metadata) {
        for (String key : PAGE_KEYS) {
            String v = metadata.get(key);
            if (v != null && !v.isBlank()) {
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException ignored) {
                    // 非数值页数（如 "unknown"）忽略，继续尝试下一个 key
                }
            }
        }
        return null;
    }
}
