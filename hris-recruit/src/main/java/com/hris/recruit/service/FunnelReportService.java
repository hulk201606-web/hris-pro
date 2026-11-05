package com.hris.recruit.service;

import com.hris.recruit.dto.PublicRecruitDtos.FunnelItem;
import com.hris.recruit.mapper.StageLogMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘漏斗报表（RCS-F-020）：按 rcu_stage_log 聚合各阶段去重候选人数与转化率。
 *
 * <p>阶段顺序读应用侧常量（与 rcu_application.stage 枚举一致）：
 * APPLIED → SCREEN → INTERVIEW → OFFER → HIRED。count = 该阶段去重投递数
 * （COUNT(DISTINCT application_id)，同一投递多次流转只计一次）；conversion = count / APPLIED count，
 * 百分比 2 位小数（APPLIED 恒为 100.00）。时间过滤取 rcu_stage_log.created_at（流转发生时间）；
 * reqId 过滤经 rcu_application.opening_id 关联（官网「职位」即需求单，见 PublicRecruitService）。</p>
 */
@Service
public class FunnelReportService {

    /** 阶段顺序（与 rcu_application.stage 注释枚举一致，唯一事实源）。 */
    private static final List<String> STAGE_ORDER =
            List.of("APPLIED", "SCREEN", "INTERVIEW", "OFFER", "HIRED");

    private final StageLogMapper stageLogMapper;

    public FunnelReportService(StageLogMapper stageLogMapper) {
        this.stageLogMapper = stageLogMapper;
    }

    /**
     * 漏斗聚合。from/to/reqId 均可选；from/to 闭区间过滤 stage_log.created_at。
     */
    public List<FunnelItem> funnel(LocalDateTime from, LocalDateTime to, Long reqId) {
        List<Map<String, Object>> rows = stageLogMapper.selectFunnelCounts(from, to, reqId);
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String stage = String.valueOf(row.get("stage"));
            counts.put(stage, ((Number) row.get("cnt")).longValue());
        }
        long applied = counts.getOrDefault(STAGE_ORDER.get(0), 0L);
        return STAGE_ORDER.stream()
                .map(stage -> {
                    long count = counts.getOrDefault(stage, 0L);
                    BigDecimal conversion = applied == 0
                            ? BigDecimal.ZERO.setScale(2)
                            : BigDecimal.valueOf(count * 100L)
                                    .divide(BigDecimal.valueOf(applied), 2, RoundingMode.HALF_UP);
                    return new FunnelItem(stage, count, conversion);
                })
                .toList();
    }
}
