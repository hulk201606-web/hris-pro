package com.hris.employee.service;

import com.hris.common.api.R;
import com.hris.employee.client.PlatformConfigClient;
import com.hris.employee.util.CodeUtil;
import com.hris.employee.util.EmpNoPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * 工号生成（EMP-F-002）。
 *
 * <p>租户配置 {@code sys_tenant_config['emp.no.pattern']} 驱动，如
 * {@code {PREFIX}{YYYY}{SEQ:4}} → {@code e20260001}。配置缺失、格式非法或
 * platform 不可用时回退原雪花策略 {@link CodeUtil#nextEmpNo(String)}，保证建档不中断。</p>
 *
 * <p>序号段按 uk(tenant_id, prefix, seq_year) 行锁分配（{@link EmpNoSeqAllocator}），
 * 按年归零；不同前缀各自独立递增。</p>
 */
@Service
public class EmpNoService {

    private static final Logger log = LoggerFactory.getLogger(EmpNoService.class);

    /** 租户配置键（PLT-F-010 系统配置中心）。 */
    public static final String CONFIG_KEY = "emp.no.pattern";

    private final PlatformConfigClient configClient;
    private final EmpNoSeqAllocator seqAllocator;

    public EmpNoService(PlatformConfigClient configClient, EmpNoSeqAllocator seqAllocator) {
        this.configClient = configClient;
        this.seqAllocator = seqAllocator;
    }

    /** 生成工号：模板可用则用租户序号段，否则回退雪花。 */
    public String next(String prefix) {
        String pattern = loadPattern();
        if (!EmpNoPattern.isSupported(pattern)) {
            return CodeUtil.nextEmpNo(prefix);
        }
        int year = Year.now().getValue();
        String resolvedPrefix = prefix == null || prefix.isBlank() ? "e" : prefix.trim().toLowerCase();
        int seq = seqAllocator.allocate(resolvedPrefix, year);
        return EmpNoPattern.render(pattern, resolvedPrefix, year, seq);
    }

    /** 读取模板配置，任何异常都降级为缺省策略（工号生成不能成为建档单点）。 */
    private String loadPattern() {
        try {
            R<String> resp = configClient.getTenantConfig(CONFIG_KEY);
            return resp == null ? null : resp.getData();
        } catch (Exception e) {
            log.warn("读取租户工号模板失败，回退缺省策略: {}", e.getMessage());
            return null;
        }
    }
}
