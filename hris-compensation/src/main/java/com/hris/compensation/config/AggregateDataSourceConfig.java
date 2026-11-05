package com.hris.compensation.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 聚合只读数据源（批 8 #17 MVP，CMP-F-017 报表链路）。
 *
 * <p>配置驱动切换：yml 配置 spring.datasource.aggregate.url 时创建独立只读数据源
 * （Hikari，readOnly=true 标识，报表聚合查询路由到它）；缺省未配置时回退主库
 * （AggregateDataSourceProvider 兜底），无需独立数据库即可部署。</p>
 *
 * <p>局限：当前为「同一实例可选第二数据源」MVP，真·独立聚合库由 DBA 供数后仅改
 * aggregate.url 即可切换（配置、代码均无需变更）。主库事务不受影响：报表查询走
 * 独立 JdbcTemplate（aggregateJdbcTemplate），主库 mapper/事务仍走自动配置主库。</p>
 */
@Configuration
public class AggregateDataSourceConfig {

    /**
     * 聚合只读数据源（仅当 spring.datasource.aggregate.url 显式配置时创建）。
     * 若聚合库账号本身只读，可在 url/账号侧收敛；连接层 readOnly 标识防误写。
     */
    @Bean("aggregateDataSource")
    @ConditionalOnProperty("spring.datasource.aggregate.url")
    public DataSource aggregateDataSource(
            @Value("${spring.datasource.aggregate.url}") String url,
            @Value("${spring.datasource.aggregate.username:${DB_USERNAME:root}}") String username,
            @Value("${spring.datasource.aggregate.password:${DB_PASSWORD:}}") String password,
            @Value("${spring.datasource.aggregate.driver-class-name:${DB_DRIVER:com.mysql.cj.jdbc.Driver}}")
            String driverClassName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(4);
        ds.setPoolName("cmp-aggregate-pool");
        return ds;
    }

    /**
     * 报表用 JdbcTemplate：聚合数据源已配置则路由其上，否则回退主库。
     * 独立于自动配置 jdbcTemplate，主库事务/连接池行为不变。
     */
    @Bean("aggregateJdbcTemplate")
    public org.springframework.jdbc.core.JdbcTemplate aggregateJdbcTemplate(
            AggregateDataSourceProvider provider) {
        return new org.springframework.jdbc.core.JdbcTemplate(provider.get());
    }
}
