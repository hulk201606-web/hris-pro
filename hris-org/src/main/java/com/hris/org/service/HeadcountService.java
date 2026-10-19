package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.HeadcountRequest;
import com.hris.org.dto.HeadcountUsage;
import com.hris.org.dto.HcCheckResponse;
import com.hris.org.entity.OrgHeadcount;
import com.hris.org.mapper.OrgHeadcountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 编制计划 + 占用看板 + 编制校验（ORG-F-009/010/011）。
 */
@Service
public class HeadcountService {

    private final OrgHeadcountMapper mapper;

    public HeadcountService(OrgHeadcountMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 编制查询：可按部门/岗位/季度（yyyyQn）过滤，返回额度、已占用、空缺、占用率。
     * occupied 由员工入职/离职流程回写。
     */
    public List<HeadcountUsage> query(Long deptId, Long positionId, String period) {
        LambdaQueryWrapper<OrgHeadcount> qw = new LambdaQueryWrapper<>();
        if (deptId != null) {
            qw.eq(OrgHeadcount::getDeptId, deptId);
        }
        if (positionId != null) {
            qw.eq(OrgHeadcount::getPositionId, positionId);
        }
        if (period != null && !period.isBlank()) {
            qw.eq(OrgHeadcount::getPeriod, period);
        }
        return mapper.selectList(qw).stream().map(this::toUsage).toList();
    }

    /**
     * 设定编制额度：部门+岗位+季度三元组唯一，已存在则覆盖 quota，否则新建且 occupied=0。
     */
    @Transactional(rollbackFor = Exception.class)
    public HeadcountUsage set(HeadcountRequest req) {
        OrgHeadcount existing = mapper.selectOne(new LambdaQueryWrapper<OrgHeadcount>()
                .eq(OrgHeadcount::getDeptId, req.deptId())
                .eq(OrgHeadcount::getPositionId, req.positionId())
                .eq(OrgHeadcount::getPeriod, req.period()));
        if (existing != null) {
            existing.setQuota(req.quota());
            mapper.updateById(existing);
            return toUsage(existing);
        }
        OrgHeadcount hc = new OrgHeadcount();
        hc.setDeptId(req.deptId());
        hc.setPositionId(req.positionId());
        hc.setPeriod(req.period());
        hc.setQuota(req.quota());
        hc.setOccupied(0);
        mapper.insert(hc);
        return toUsage(hc);
    }

    /** 编制校验（内部接口，超编时返回 shortfall）。 */
    public HcCheckResponse check(Long deptId, Long positionId, Integer count) {
        String period = currentPeriod();
        OrgHeadcount hc = mapper.selectOne(new LambdaQueryWrapper<OrgHeadcount>()
                .eq(OrgHeadcount::getDeptId, deptId)
                .eq(OrgHeadcount::getPositionId, positionId)
                .eq(OrgHeadcount::getPeriod, period));
        int quota = hc == null ? 0 : (hc.getQuota() == null ? 0 : hc.getQuota());
        int occupied = hc == null ? 0 : (hc.getOccupied() == null ? 0 : hc.getOccupied());
        int need = count == null ? 0 : count;
        int shortfall = need - (quota - occupied);
        if (shortfall < 0) {
            shortfall = 0;
        }
        return new HcCheckResponse(shortfall == 0, quota, occupied, shortfall);
    }

    private HeadcountUsage toUsage(OrgHeadcount hc) {
        int quota = hc.getQuota() == null ? 0 : hc.getQuota();
        int occupied = hc.getOccupied() == null ? 0 : hc.getOccupied();
        int vacancy = quota - occupied;
        double rate = quota <= 0 ? 0 : (double) occupied / quota;
        return new HeadcountUsage(hc.getDeptId(), hc.getPositionId(), hc.getPeriod(),
                quota, occupied, vacancy, rate);
    }

    private String currentPeriod() {
        LocalDate today = LocalDate.now();
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        return today.getYear() + "Q" + quarter;
    }
}
