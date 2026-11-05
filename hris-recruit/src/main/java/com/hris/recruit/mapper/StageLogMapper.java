package com.hris.recruit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.recruit.entity.StageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * rcu_stage_log 数据访问。
 *
 * <p>漏斗聚合（RCS-F-020）：自写 join SQL（租户插件自动追加 tenant_id 条件；
 * 逻辑删除为手写 SQL，需显式 deleted=0）。count 用 COUNT(DISTINCT application_id)，
 * 同一投递多次流转只计一次。</p>
 */
@Mapper
public interface StageLogMapper extends BaseMapper<StageLog> {

    /**
     * 漏斗各阶段去重投递计数。from/to 闭区间过滤 stage_log.created_at；
     * reqId 经 rcu_application.opening_id 关联过滤（官网「职位」= 需求单，见 PublicRecruitService）。
     */
    @Select("""
            <script>
            SELECT sl.to_stage AS stage, COUNT(DISTINCT sl.application_id) AS cnt
            FROM rcu_stage_log sl
            INNER JOIN rcu_application a ON a.id = sl.application_id AND a.deleted = 0
            WHERE sl.deleted = 0
            <if test="from != null">AND sl.created_at &gt;= #{from}</if>
            <if test="to != null">AND sl.created_at &lt;= #{to}</if>
            <if test="reqId != null">AND a.opening_id = #{reqId}</if>
            GROUP BY sl.to_stage
            </script>
            """)
    List<Map<String, Object>> selectFunnelCounts(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 @Param("reqId") Long reqId);
}
