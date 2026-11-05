package com.hris.compensation.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 聚合数据源路由提供器（批 8 #17 MVP）。
 *
 * <p>路由选择：aggregateDataSource（spring.datasource.aggregate.url 显式配置时创建）
 * 存在则返回聚合库；否则回退主库 dataSource（配置驱动，无需独立数据库即可部署）。
 * 报表查询经 aggregateJdbcTemplate 走本路由，主库 mapper/事务链路不受影响。</p>
 */
@Component
public class AggregateDataSourceProvider {

    private final ObjectProvider<DataSource> aggregate;
    private final DataSource primary;

    public AggregateDataSourceProvider(
            @Qualifier("aggregateDataSource") ObjectProvider<DataSource> aggregate,
            @Qualifier("dataSource") DataSource primary) {
        this.aggregate = aggregate;
        this.primary = primary;
    }

    /** 路由选择：聚合数据源已配置 → 聚合库；否则 → 主库。 */
    public DataSource get() {
        DataSource agg = aggregate.getIfAvailable();
        return agg != null ? agg : primary;
    }

    /** 聚合数据源是否已配置（观测/单测用）。 */
    public boolean aggregateConfigured() {
        return aggregate.getIfAvailable() != null;
    }
}
