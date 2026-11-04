package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.CandidateDedupDtos.CandidateDedupItem;
import com.hris.recruit.dto.CandidateDedupDtos.CandidateDedupResponse;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.mapper.CandidateMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人查重（RCS-F-009）：按邮箱精确匹配、姓名辅助匹配，命中后提示合并。
 *
 * <p><b>为何单独建这个 Service 而不是改 CandidateService</b>：
 * hris-recruit 的 Controller / Service / DTO 由 scripts/gen_phase2.py 从 DDL
 * 生成，重跑生成器会覆盖手写改动。查重属于生成骨架之外的业务逻辑，
 * 放进独立文件才能与生成器共存。</p>
 *
 * <p>匹配维度取舍见 {@link com.hris.recruit.dto.CandidateDedupDtos} 类注释：
 * 邮箱可靠、姓名仅辅助、手机号因由调用方传入密文而暂不纳入。</p>
 */
@Service
public class CandidateDedupService {

    private final CandidateMapper candidateMapper;

    public CandidateDedupService(CandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    /**
     * 查重。email 与 name 至少传一个，否则抛参数错误（避免全表捞取）。
     *
     * <p>返回顺序：邮箱命中在前（强信号），姓名命中在后（弱信号）。
     * 同一候选人若两个维度都命中，只保留邮箱那条，不重复列出。</p>
     */
    public CandidateDedupResponse check(String email, String name) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasName = name != null && !name.isBlank();
        if (!hasEmail && !hasName) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮箱与姓名至少提供一个");
        }

        // LinkedHashMap 以 id 去重，保持「邮箱命中优先」的顺序
        Map<Long, CandidateDedupItem> merged = new LinkedHashMap<>();

        if (hasEmail) {
            LambdaQueryWrapper<Candidate> qw = new LambdaQueryWrapper<>();
            qw.eq(Candidate::getEmail, email.trim());
            for (Candidate c : candidateMapper.selectList(qw)) {
                merged.put(c.getId(), toItem(c, "EMAIL"));
            }
        }
        if (hasName) {
            LambdaQueryWrapper<Candidate> qw = new LambdaQueryWrapper<>();
            qw.eq(Candidate::getName, name.trim());
            for (Candidate c : candidateMapper.selectList(qw)) {
                // 已因邮箱命中则不重复放入（保留 EMAIL 这个更强的 matchField）
                merged.putIfAbsent(c.getId(), toItem(c, "NAME"));
            }
        }

        List<CandidateDedupItem> items = new ArrayList<>(merged.values());
        return new CandidateDedupResponse(!items.isEmpty(), items.size(), items);
    }

    private CandidateDedupItem toItem(Candidate c, String matchField) {
        return new CandidateDedupItem(c.getId(), c.getName(), c.getEmail(),
                c.getMobileMask(), c.getStatus(), matchField);
    }
}
