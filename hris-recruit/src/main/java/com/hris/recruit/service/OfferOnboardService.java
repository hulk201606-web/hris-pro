package com.hris.recruit.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.EmployeeClient;
import com.hris.recruit.entity.Application;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.OfferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offer 录用 → 员工建档（RCU-F-030 跨域联动）。
 *
 * <p>流程：offer → application（取 candidateId）→ candidate（取姓名/手机/邮箱）→ 员工域预建档。
 * 成功后标记 offer=ACCEPTED、candidate=HIRED。幂等：已录用则拒绝重复建档（docs/07 跨域契约）。</p>
 */
@Service
public class OfferOnboardService {

    private final OfferMapper offerMapper;
    private final ApplicationMapper applicationMapper;
    private final CandidateMapper candidateMapper;
    private final EmployeeClient employeeClient;

    public OfferOnboardService(OfferMapper offerMapper, ApplicationMapper applicationMapper,
                              CandidateMapper candidateMapper, EmployeeClient employeeClient) {
        this.offerMapper = offerMapper;
        this.applicationMapper = applicationMapper;
        this.candidateMapper = candidateMapper;
        this.employeeClient = employeeClient;
    }

    /** 录用建档：跨域调用员工域 /internal/emp/pre-create。 */
    @Transactional(rollbackFor = Exception.class)
    public Long acceptAndOnboard(Long offerId) {
        Offer offer = offerMapper.selectById(offerId);
        if (offer == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
        if ("ACCEPTED".equals(offer.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Offer 已录用，不可重复建档");
        }
        // 可录用态守卫（docs/19 §8.5-6）：仅已发/已接受可转入职，DRAFT/REJECTED 拒绝
        if (!"SENT".equals(offer.getStatus()) && !"ACCEPTED".equals(offer.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Offer 状态为 " + offer.getStatus() + "，不可转入职");
        }
        Application application = applicationMapper.selectById(offer.getApplicationId());
        if (application == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "投递记录不存在");
        }
        Candidate candidate = candidateMapper.selectById(application.getCandidateId());
        if (candidate == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "候选人不存在");
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", candidate.getName());
        req.put("mobile", candidate.getMobileCipher());
        req.put("email", candidate.getEmail());
        req.put("empNoPrefix", "RC");
        R<Long> empR = employeeClient.preCreate(req);
        if (empR == null || !empR.isSuccess() || empR.getData() == null) {
            throw new BizException(ErrorCode.RCS_ERROR, "员工建档失败");
        }
        Long empId = empR.getData();

        offer.setStatus("ACCEPTED");
        offerMapper.updateById(offer);
        candidate.setStatus("HIRED");
        candidateMapper.updateById(candidate);
        return empId;
    }
}
