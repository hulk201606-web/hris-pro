package com.hris.recruit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResumeTextExtractor 单元测试（RCS-F-007）：email/手机号抽取、姓名启发、段落分段。
 */
class ResumeTextExtractorTest {

    @Test
    @DisplayName("email 抽取：标准邮箱命中，返回首个匹配")
    void extractEmail() {
        String text = "联系方式：zhangsan.test@company.com.cn，电话见下。";
        assertEquals("zhangsan.test@company.com.cn", ResumeTextExtractor.extractEmail(text));
    }

    @Test
    @DisplayName("email 抽取：无邮箱返回 null")
    void extractEmailMissing() {
        assertNull(ResumeTextExtractor.extractEmail("无邮箱联系方式"));
        assertNull(ResumeTextExtractor.extractEmail(null));
    }

    @Test
    @DisplayName("手机号抽取：大陆 11 位命中")
    void extractMobile() {
        assertEquals("13812348888", ResumeTextExtractor.extractMobile("手机：13812348888"));
        assertEquals("15912345678", ResumeTextExtractor.extractMobile("电话 159-1234-5678 备用 13812348888"
                .replace("-", "")));
    }

    @Test
    @DisplayName("手机号抽取：非大陆号段不误报")
    void extractMobileRejectsInvalid() {
        assertNull(ResumeTextExtractor.extractMobile("号码 12345678901 或 1381234888"));
    }

    @Test
    @DisplayName("姓名启发：开头 30 字符内首个 2-4 连续中文")
    void extractName() {
        assertEquals("张三", ResumeTextExtractor.extractName("张三\n男 | 28岁\nzhangsan@x.com"));
        assertEquals("李小云", ResumeTextExtractor.extractName("李小云 求职意向：Java 工程师"));
    }

    @Test
    @DisplayName("姓名启发：无中文姓名命中返回 null（纯英文简历）")
    void extractNameMissing() {
        assertNull(ResumeTextExtractor.extractName("John Smith\njohn@x.com"));
    }

    @Test
    @DisplayName("分段：教育/工作/项目标题行切分，其余标题行忽略")
    void splitSections() {
        String text = """
                李小云
                13812348888 | lixy@x.com

                教育经历
                2014-2018 华中科技大学 计算机科学与技术 本科

                工作经历
                2018-2021 阿里巴巴 Java 开发工程师
                负责订单系统

                项目经历
                电商中台重构项目
                担任后端负责人

                技能
                Java/Spring/MySQL
                """;
        List<String> edu = new ArrayList<>();
        List<String> exp = new ArrayList<>();
        ResumeTextExtractor.splitSections(text, edu, exp);

        assertEquals(1, edu.size());
        assertTrue(edu.get(0).contains("华中科技大学"), "教育行归 education");
        assertEquals(4, exp.size(), "工作与项目行合并进 experience");
        assertTrue(exp.get(0).contains("阿里巴巴"));
        assertTrue(exp.get(2).contains("电商中台重构项目"));
        assertTrue(exp.get(3).contains("担任后端负责人"));
    }

    @Test
    @DisplayName("extract 汇总：各字段一次抽取，空段落为空列表")
    void extractSummary() {
        ResumeTextExtractor.ExtractResult r =
                ResumeTextExtractor.extract("王二\n手机 13812348888 邮箱 wang2@x.com");
        assertEquals("王二", r.name());
        assertEquals("13812348888", r.mobile());
        assertEquals("wang2@x.com", r.email());
        assertTrue(r.educationLines().isEmpty());
        assertTrue(r.experienceLines().isEmpty());
    }
}
