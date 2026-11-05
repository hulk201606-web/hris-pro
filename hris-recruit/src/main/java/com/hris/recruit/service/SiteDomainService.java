package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainCreateRequest;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainResponse;
import com.hris.recruit.dto.SiteDomainDtos.SiteDomainUpdateRequest;
import com.hris.recruit.entity.SiteDomain;
import com.hris.recruit.mapper.SiteDomainMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 官网租户域名映射服务（批 7 #13）：管理端 CRUD + 公开链路按域名解析租户。
 *
 * <p>语义：
 * <ul>
 *   <li>uk(domain) 全局唯一——一个域名只能映射一个租户；uk 含逻辑删除行，删除后同域名
 *       重建会冲突，建议下线用 status=DISABLED 而非物理语义删除；</li>
 *   <li>管理端 CRUD 走租户插件隔离（本租户只能管理自己的映射）；域名全局唯一校验在插入时
 *       由 uk 兜底，跨租户冲突转 PARAM_INVALID「域名已被映射」；</li>
 *   <li>公开解析 findEnabledByDomain 绕过租户插件（尚无上下文），仅返回 ENABLED。</li>
 * </ul></p>
 */
@Service
public class SiteDomainService {

    /** 启用状态。 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 停用状态。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private final SiteDomainMapper mapper;

    public SiteDomainService(SiteDomainMapper mapper) {
        this.mapper = mapper;
    }

    /** 公开链路：按域名查启用映射（跨租户）；未命中返回 null（调用方回退显式参数）。 */
    public SiteDomain findEnabledByDomain(String domain) {
        return mapper.findEnabledByDomain(domain);
    }

    /** 分页列表（租户隔离）。 */
    public PageResult<SiteDomainResponse> list(long page, long size) {
        Page<SiteDomain> p = mapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SiteDomain>().orderByDesc(SiteDomain::getCreatedAt));
        List<SiteDomainResponse> records = p.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, p.getTotal());
    }

    /** 新建映射：域名归一化（小写去空白），uk 冲突转业务错误。 */
    @Transactional(rollbackFor = Exception.class)
    public SiteDomainResponse create(SiteDomainCreateRequest req) {
        SiteDomain e = new SiteDomain();
        e.setDomain(normalize(req.domain()));
        e.setStatus(req.status() == null || req.status().isBlank() ? STATUS_ENABLED : req.status());
        if (!STATUS_ENABLED.equals(e.getStatus()) && !STATUS_DISABLED.equals(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 仅支持 ENABLED/DISABLED");
        }
        try {
            mapper.insert(e);
        } catch (DuplicateKeyException dup) {
            throw new BizException(ErrorCode.PARAM_INVALID, "域名已被映射（含历史记录），请更换域名");
        }
        return toResponse(e);
    }

    /** 更新映射：可改域名与状态；域名归一化后 uk 冲突转业务错误。 */
    @Transactional(rollbackFor = Exception.class)
    public SiteDomainResponse update(Long id, SiteDomainUpdateRequest req) {
        SiteDomain e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "域名映射不存在");
        }
        if (req.domain() != null && !req.domain().isBlank()) {
            e.setDomain(normalize(req.domain()));
        }
        if (req.status() != null && !req.status().isBlank()) {
            if (!STATUS_ENABLED.equals(req.status()) && !STATUS_DISABLED.equals(req.status())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "status 仅支持 ENABLED/DISABLED");
            }
            e.setStatus(req.status());
        }
        try {
            mapper.updateById(e);
        } catch (DuplicateKeyException dup) {
            throw new BizException(ErrorCode.PARAM_INVALID, "域名已被映射（含历史记录），请更换域名");
        }
        return toResponse(e);
    }

    /** 删除映射（逻辑删除）；下线场景建议改 status=DISABLED 保留历史。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "域名映射不存在");
        }
    }

    /** 域名归一化：trim + 小写 + 拒绝协议/端口/路径形态。 */
    private String normalize(String domain) {
        String d = domain == null ? "" : domain.strip().toLowerCase();
        if (d.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "domain 不能为空");
        }
        if (d.contains("://") || d.contains("/") || d.contains(":") || d.contains("?")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "domain 仅填主机名，不含协议/端口/路径");
        }
        return d;
    }

    private SiteDomainResponse toResponse(SiteDomain e) {
        return new SiteDomainResponse(e.getId(), e.getTenantId(), e.getDomain(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
