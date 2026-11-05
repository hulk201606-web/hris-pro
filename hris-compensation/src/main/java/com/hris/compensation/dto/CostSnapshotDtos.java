package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 法人成本快照 DTO（批 12 #29：聚合库物化）。
 *
 * <p>与 {@link ReportDtos.LegalEntityCostRow} 的区别：快照行多带 {@code refreshedAt}
 * （物化时间，报表可据此判断数据新鲜度），且 {@code legalEntityId} 用 0 表示法人未知
 * （库内哨兵值），出参时再还原为 null。</p>
 */
public final class CostSnapshotDtos {

    private CostSnapshotDtos() {
    }

    /** 快照行。 */
    public record CostSnapshotRow(Long legalEntityId, int headcount, BigDecimal grossTotal,
                                  BigDecimal deductionTotal, BigDecimal taxTotal,
                                  BigDecimal netTotal, LocalDateTime refreshedAt) {
    }

    /**
     * 刷新结果：rows=本期落库行数，affected=upsert 影响行数（MySQL 语义：插入=1，
     * 更新=2 或 0），refreshedAt=本次快照时间。
     */
    public record RefreshResponse(String periodCode, int rows, int affected,
                                  LocalDateTime refreshedAt) {
    }
}
