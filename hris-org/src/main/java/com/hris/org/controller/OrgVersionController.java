package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.OrgVersionDtos.ActivateDueResponse;
import com.hris.org.dto.OrgVersionDtos.OrgVersionCreateRequest;
import com.hris.org.dto.OrgVersionDtos.OrgVersionDetail;
import com.hris.org.dto.OrgVersionDtos.OrgVersionSummary;
import com.hris.org.enums.OrgVersionStatus;
import com.hris.org.service.OrgVersionService;
import com.hris.org.service.OrgVersionStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 组织版本端点（批 8 #16 MVP；批 11 #23 草稿状态机）。
 *
 * <p>状态可见性：列表默认只返回 ACTIVE；status=DRAFT/ALL 需要 org:dept:write（草稿仅管理端可见）。
 * 到期激活 {@code POST /versions/activate-due} 由 platform 调度中心日任务触发（内部端点同时提供
 * {@code POST /internal/org/versions/activate-due} 给无 JWT 的调度线程）。部门维度沿用
 * org_dept_history 既有快照机制，不在此端点重复承载。权限复用 org:dept:*（不新增权限点）。</p>
 */
@RestController
@RequestMapping("/api/v1/org")
public class OrgVersionController {

    /** 管理端写权限点（同时是草稿可见性判据）。 */
    private static final String WRITE_PERM = "org:dept:write";

    private final OrgVersionService versionService;
    private final OrgVersionStateService stateService;

    public OrgVersionController(OrgVersionService versionService, OrgVersionStateService stateService) {
        this.versionService = versionService;
        this.stateService = stateService;
    }

    /**
     * 创建组织版本：全量快照全部岗位（含挂靠部门）与职级，返回版本号与两维度计数。
     * 新版本状态固定 DRAFT；body 可缺省（effectiveFrom 为空 = 当天）。
     */
    @RequirePerm(WRITE_PERM)
    @PostMapping("/versions")
    public R<OrgVersionSummary> create(@RequestBody(required = false) OrgVersionCreateRequest req) {
        return R.ok(versionService.create(req == null ? null : req.effectiveFrom()));
    }

    /**
     * 版本列表（按创建先后倒序）。
     *
     * @param status ACTIVE（缺省）/DRAFT/SUPERSEDED/ALL；DRAFT 与 ALL 含草稿，需写权限
     */
    @RequirePerm("org:dept:read")
    @GetMapping("/versions")
    public R<List<OrgVersionSummary>> list(@RequestParam(required = false) String status) {
        OrgVersionStatus filter = parseFilter(status);
        if (filter == null || filter == OrgVersionStatus.DRAFT) {
            requireWrite("草稿版本仅管理端可见");
        }
        return R.ok(versionService.list(filter));
    }

    /** 版本查看：DRAFT 仅管理端可读（无写权限时按不存在处理，避免泄露草稿内容）。 */
    @RequirePerm("org:dept:read")
    @GetMapping("/versions/{versionNo}")
    public R<OrgVersionDetail> get(@PathVariable String versionNo) {
        return R.ok(versionService.get(versionNo, hasWrite()));
    }

    /** 手动激活版本（管理端显式操作，不受生效日限制；原 ACTIVE 置 SUPERSEDED）。 */
    @RequirePerm(WRITE_PERM)
    @PostMapping("/versions/{versionNo}/activate")
    public R<OrgVersionSummary> activate(@PathVariable String versionNo) {
        stateService.activate(versionNo);
        return R.ok(versionService.summary(versionNo));
    }

    /**
     * 到期激活（调度中心日任务调用，按租户执行）：DRAFT 且 effective_from &lt;= 今天
     * 的批次逐个激活。幂等：重复调用无到期批次时 activated=0。
     */
    @RequirePerm(WRITE_PERM)
    @PostMapping("/versions/activate-due")
    public R<ActivateDueResponse> activateDue() {
        LocalDate today = LocalDate.now();
        return R.ok(new ActivateDueResponse(today, stateService.activateDue(today)));
    }

    /** 版本回滚：按快照恢复主数据并激活该历史版本（草稿不可回滚）。 */
    @RequirePerm(WRITE_PERM)
    @PostMapping("/versions/{versionNo}/rollback")
    public R<OrgVersionSummary> rollback(@PathVariable String versionNo) {
        return R.ok(versionService.rollback(versionNo));
    }

    private OrgVersionStatus parseFilter(String status) {
        if (status == null || status.isBlank()) {
            return OrgVersionStatus.ACTIVE;
        }
        if ("ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        OrgVersionStatus parsed = OrgVersionStatus.from(status);
        if (parsed == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 仅支持 ACTIVE/DRAFT/SUPERSEDED/ALL");
        }
        return parsed;
    }

    private boolean hasWrite() {
        Set<String> perms = AuthContextHolder.getPerms();
        return perms != null && perms.contains(WRITE_PERM);
    }

    private void requireWrite(String message) {
        if (!hasWrite()) {
            throw new BizException(ErrorCode.FORBIDDEN, message);
        }
    }
}
