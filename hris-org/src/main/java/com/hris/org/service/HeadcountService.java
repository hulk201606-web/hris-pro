package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.HeadcountRequest;
import com.hris.org.dto.HeadcountUsage;
import com.hris.org.dto.HcCheckResponse;
import com.hris.org.dto.OccupancyRequest;
import com.hris.org.entity.OrgHeadcount;
import com.hris.org.entity.OrgHcOccupancy;
import com.hris.org.mapper.OrgHeadcountMapper;
import com.hris.org.mapper.OrgHcOccupancyMapper;
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
    private final OrgHcOccupancyMapper occupancyMapper;

    public HeadcountService(OrgHeadcountMapper mapper, OrgHcOccupancyMapper occupancyMapper) {
        this.mapper = mapper;
        this.occupancyMapper = occupancyMapper;
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

    /**
     * 编制占用回写（§10.5-B8：员工入职/离职/调动落账后由 emp 经内部端点调用）。
     *
     * <p>幂等：以 transNo 查 org_hc_occupancy，命中即视为已处理直接返回（不重复计）。
     * period 为空按当前季度；按 dept+position+period 三元组定位编制行，缺行 PARAM_INVALID；
     * occupied 回写 delta（+1/-1），结果为负则 PARAM_INVALID 守卫并回滚。
     * 占用流水 changeType 按方向记 OCCUPY/RELEASE。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void occupancy(OccupancyRequest req) {
        if (req.delta() == null || (req.delta() != 1 && req.delta() != -1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "delta 仅允许 +1/-1");
        }
        Long existed = occupancyMapper.selectCount(new LambdaQueryWrapper<OrgHcOccupancy>()
                .eq(OrgHcOccupancy::getTransNo, req.transNo()));
        if (existed != null && existed > 0) {
            return; // 幂等：同事务号已回写过
        }
        String period = req.period() == null || req.period().isBlank()
                ? currentPeriod() : req.period();
        OrgHeadcount hc = mapper.selectOne(new LambdaQueryWrapper<OrgHeadcount>()
                .eq(OrgHeadcount::getDeptId, req.deptId())
                .eq(OrgHeadcount::getPositionId, req.positionId())
                .eq(OrgHeadcount::getPeriod, period));
        if (hc == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "编制计划不存在: dept=" + req.deptId() + " position=" + req.positionId() + " period=" + period);
        }
        int occupied = (hc.getOccupied() == null ? 0 : hc.getOccupied()) + req.delta();
        if (occupied < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "编制占用不可为负");
        }
        hc.setOccupied(occupied);
        mapper.updateById(hc);

        OrgHcOccupancy row = new OrgHcOccupancy();
        row.setEmpId(req.empId());
        row.setChangeType(req.delta() > 0 ? "OCCUPY" : "RELEASE");
        row.setDeptId(req.deptId());
        row.setPositionId(req.positionId());
        row.setTransNo(req.transNo());
        occupancyMapper.insert(row);
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
