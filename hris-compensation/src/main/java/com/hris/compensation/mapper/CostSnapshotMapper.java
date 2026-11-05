package com.hris.compensation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.compensation.entity.CostSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * cmp_cost_snapshot 数据访问（批 12 #29）。
 *
 * <p>写路径走主库：{@link #upsert} 用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 命唯一键
 * {@code uk(tenant_id, period_code, legal_entity_id)}，避免「先查后写」的并发双插；
 * {@link #deleteStale} 物理删除本期已不存在的法人行（物化数据可重算，无需逻辑删除留痕，
 * 且逻辑删除会占住唯一键导致重新插入冲突）。</p>
 *
 * <p>以 MySQL 语法落地（本服务运行库为 MySQL 8.0；PostgreSQL 段仅作基线 DDL）。</p>
 */
@Mapper
public interface CostSnapshotMapper extends BaseMapper<CostSnapshot> {

    /** 按唯一键 upsert（含 refreshed_at 与公共列，deleted 复位为 0）。 */
    @Insert("""
            INSERT INTO cmp_cost_snapshot
              (id, tenant_id, period_code, legal_entity_id, headcount, gross_total,
               deduction_total, tax_total, net_total, refreshed_at,
               created_by, updated_by, created_at, updated_at, deleted, version)
            VALUES
              (#{id}, #{tenantId}, #{periodCode}, #{legalEntityId}, #{headcount}, #{grossTotal},
               #{deductionTotal}, #{taxTotal}, #{netTotal}, #{refreshedAt},
               #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, 0, 0)
            ON DUPLICATE KEY UPDATE
              headcount       = VALUES(headcount),
              gross_total     = VALUES(gross_total),
              deduction_total = VALUES(deduction_total),
              tax_total       = VALUES(tax_total),
              net_total       = VALUES(net_total),
              refreshed_at    = VALUES(refreshed_at),
              updated_by      = VALUES(updated_by),
              updated_at      = VALUES(updated_at),
              deleted         = 0
            """)
    int upsert(CostSnapshot snapshot);

    /**
     * 删除本期已不在重算结果中的法人行（ids 为空 = 清空本期快照）。
     * 物化快照为派生数据，物理删除后可由刷新端点重建。
     */
    @Delete("""
            <script>
            DELETE FROM cmp_cost_snapshot
            WHERE tenant_id = #{tenantId} AND period_code = #{periodCode}
            <if test="legalEntityIds != null and legalEntityIds.size() > 0">
              AND legal_entity_id NOT IN
              <foreach collection="legalEntityIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            </script>
            """)
    int deleteStale(@Param("tenantId") Long tenantId,
                    @Param("periodCode") String periodCode,
                    @Param("legalEntityIds") Collection<Long> legalEntityIds);
}
