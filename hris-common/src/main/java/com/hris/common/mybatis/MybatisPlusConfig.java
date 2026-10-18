package com.hris.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.hris.common.trace.TraceContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：租户隔离（全局兜底）+ 乐观锁 + 分页 + 公共字段自动填充。
 *
 * <p>对应 docs/08 §3 纵深第三层「ORM 层 TenantLineInnerInterceptor 自动注入 tenant_id 条件，
 * 忘写也拦截」；docs/12 §2 时间戳由 MetaObjectHandler 应用层填充（双库一致，MySQL 不用 ON UPDATE、
 * PG 不用触发器）。</p>
 */
@Configuration
@ConditionalOnClass(TenantLineInnerInterceptor.class)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 多租户插件（全局兜底；跨租户运营统计需显式 @IgnoreTenant）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TraceContext.getTenantId();
                if (tenantId == null) {
                    // 无租户上下文（如平台级启动任务）返回 0，避免空指针；业务查询必须带租户
                    tenantId = 0L;
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 平台级无租户表（广播字典表带 tenant_id，仍拦截；此处仅豁免真正的平台表）
                return false;
            }
        }));
        // 2. 乐观锁（version 字段）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 3. 分页（不指定 DbType，运行时按数据源方言自动推断，兼容 MySQL / PostgreSQL 双库）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                Long userId = TraceContext.getUserId() == null ? 0L : TraceContext.getUserId();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "createdBy", Long.class, userId);
                strictInsertFill(metaObject, "updatedBy", Long.class, userId);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                Long userId = TraceContext.getUserId() == null ? 0L : TraceContext.getUserId();
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
            }
        };
    }
}
