package com.hris.rag.service;

import com.hris.rag.dto.FileTextDtos.ExtractTextResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件文本层抽取单测（批 12 #28）：纯文本 / DOCX 文本层 / 扫描件（无文本层）提示 / 空文件。
 *
 * <p>扫描件用「无文本对象的 PDF」模拟（真实扫描件即图片型 PDF，文本层为空），
 * 不引入 OCR 依赖即可验证降级契约。</p>
 */
class FileTextExtractorTest {

    private final FileTextExtractor extractor = new FileTextExtractor();

    @Test
    void extractsPlainTextLayer() {
        byte[] content = "张三 13800138000 zhangsan@example.com\n5 年 Java 经验"
                .getBytes(StandardCharsets.UTF_8);
        ExtractTextResponse r = extractor.extract(content, "resume.txt");
        assertTrue(r.hasTextLayer());
        assertTrue(r.text().contains("13800138000"));
        assertTrue(r.text().contains("Java"));
        assertNull(r.warning());
    }

    @Test
    void extractsDocxTextLayer() throws IOException {
        ExtractTextResponse r = extractor.extract(docx("李四 项目经理 2019-2024"), "resume.docx");
        assertTrue(r.hasTextLayer(), "DOCX 段落应作为文本层抽出");
        assertTrue(r.text().contains("李四"));
        assertTrue(r.text().contains("2019-2024"));
    }

    @Test
    void scannedPdfWithoutTextLayerReturnsOcrWarning() throws IOException {
        ExtractTextResponse r = extractor.extract(blankPdf(), "scan.pdf");
        assertFalse(r.hasTextLayer());
        assertEquals("", r.text());
        assertEquals(FileTextExtractor.WARN_OCR, r.warning());
        assertNotNull(r.pages());
        assertEquals(1, r.pages());
    }

    @Test
    void emptyFileReturnsWarningWithoutError() {
        ExtractTextResponse r = extractor.extract(new byte[0], "empty.pdf");
        assertFalse(r.hasTextLayer());
        assertEquals(FileTextExtractor.WARN_EMPTY, r.warning());
    }

    @Test
    void normalizeCollapsesBlankLinesAndNulls() {
        // 连续空行压缩为一个空行，行尾空白与 \u0000 清除
        assertEquals("a\n\nb", FileTextExtractor.normalize("a\u0000\n\n\nb  "));
        assertEquals("", FileTextExtractor.normalize(null));
    }

    /** 生成含文本的 DOCX。 */
    private static byte[] docx(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** 生成无任何文本对象的单页 PDF（等价扫描件）。 */
    private static byte[] blankPdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }
}
