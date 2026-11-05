package com.hris.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * kb_sensitive_word（KBD-F-017 敏感词库）。
 *
 * <p>level：BLOCK=命中即拒绝落库；WARN=正常落库但响应回传 warnings。</p>
 */
@TableName("kb_sensitive_word")
public class SensitiveWord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String word;

    private String level;

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}
