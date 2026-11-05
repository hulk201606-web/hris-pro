package com.hris.compensation.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 聚合只读数据源路由单测（批 8 #17 MVP）：配置驱动切换语义。
 */
@ExtendWith(MockitoExtension.class)
class AggregateDataSourceProviderTest {

    @Mock
    private ObjectProvider<DataSource> aggregateProvider;
    @Mock
    private DataSource aggregateDataSource;
    @Mock
    private DataSource primaryDataSource;

    @Test
    @DisplayName("已配置 aggregate.url → 报表路由到聚合数据源")
    void routesToAggregateWhenConfigured() {
        when(aggregateProvider.getIfAvailable()).thenReturn(aggregateDataSource);
        var provider = new AggregateDataSourceProvider(aggregateProvider, primaryDataSource);

        assertSame(aggregateDataSource, provider.get());
        assertTrue(provider.aggregateConfigured());
    }

    @Test
    @DisplayName("未配置 aggregate.url → 回退主库（缺省部署形态）")
    void fallsBackToPrimaryWhenAbsent() {
        when(aggregateProvider.getIfAvailable()).thenReturn(null);
        var provider = new AggregateDataSourceProvider(aggregateProvider, primaryDataSource);

        assertSame(primaryDataSource, provider.get());
        assertFalse(provider.aggregateConfigured());
    }
}
