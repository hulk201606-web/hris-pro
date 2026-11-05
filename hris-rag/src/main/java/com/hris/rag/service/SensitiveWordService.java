package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.dto.SensitiveWordDtos.SensitiveWordCreateRequest;
import com.hris.rag.dto.SensitiveWordDtos.SensitiveWordResponse;
import com.hris.rag.entity.SensitiveWord;
import com.hris.rag.mapper.SensitiveWordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词扫描与维护（KBD-F-017，docs/19 §8.2 基线失实项落地）。
 *
 * <p>扫描语义：BLOCK 命中直接抛 PARAM_INVALID「内容包含敏感词：{词}」，不落库；
 * WARN 命中放行，返回命中词列表供响应 warnings 透出；无命中返回空列表。
 * 词库按租户隔离（uk tenant_id+word），租户可经 CRUD 端点自行维护。</p>
 */
@Service
public class SensitiveWordService {

    /** 拦截级：命中即拒绝。 */
    public static final String LEVEL_BLOCK = "BLOCK";
    /** 告警级：命中放行并回传 warnings。 */
    public static final String LEVEL_WARN = "WARN";

    private final SensitiveWordMapper mapper;

    public SensitiveWordService(SensitiveWordMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前租户词库（按词排序）。 */
    public List<SensitiveWordResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                        .orderByAsc(SensitiveWord::getWord))
                .stream().map(this::toResponse).toList();
    }

    /** 新增敏感词（uk 幂等保护：重复词抛 PARAM_INVALID）。 */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveWordResponse create(SensitiveWordCreateRequest req) {
        String word = req.word() == null ? "" : req.word().trim();
        if (word.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "敏感词不能为空");
        }
        String level = normalizeLevel(req.level());
        if (mapper.selectCount(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, word)) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "敏感词已存在：" + word);
        }
        SensitiveWord e = new SensitiveWord();
        e.setWord(word);
        e.setLevel(level);
        try {
            mapper.insert(e);
        } catch (DuplicateKeyException dup) {
            throw new BizException(ErrorCode.PARAM_INVALID, "敏感词已存在：" + word);
        }
        return toResponse(e);
    }

    /** 删除敏感词；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
    }

    /**
     * 扫描文本：BLOCK 命中抛 PARAM_INVALID；WARN 命中返回去重词列表；无命中返回空列表。
     * 空文本/空词库直接放行。
     */
    public List<String> scan(String... texts) {
        List<SensitiveWord> words = mapper.selectList(null);
        if (words.isEmpty()) {
            return List.of();
        }
        Set<String> warnings = new LinkedHashSet<>();
        for (SensitiveWord w : words) {
            if (w.getWord() == null || w.getWord().isBlank() || !hit(w.getWord(), texts)) {
                continue;
            }
            if (LEVEL_BLOCK.equalsIgnoreCase(w.getLevel())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "内容包含敏感词：" + w.getWord());
            }
            warnings.add(w.getWord());
        }
        return new ArrayList<>(warnings);
    }

    private boolean hit(String word, String... texts) {
        if (texts == null) {
            return false;
        }
        for (String text : texts) {
            if (text != null && !text.isEmpty() && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return LEVEL_WARN;
        }
        String lv = level.trim().toUpperCase();
        if (!LEVEL_BLOCK.equals(lv) && !LEVEL_WARN.equals(lv)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "level 仅支持 BLOCK/WARN");
        }
        return lv;
    }

    private SensitiveWordResponse toResponse(SensitiveWord e) {
        return new SensitiveWordResponse(e.getId(), e.getWord(), e.getLevel(), e.getCreatedAt());
    }
}
