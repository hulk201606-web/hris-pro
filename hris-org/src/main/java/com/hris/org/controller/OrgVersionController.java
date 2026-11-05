package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.OrgVersionDtos.OrgVersionDetail;
import com.hris.org.dto.OrgVersionDtos.OrgVersionSummary;
import com.hris.org.service.OrgVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织版本端点（批 8 #16 MVP：组织版本扩展到岗位/职级）。
 *
 * <p>版本创建=全量快照岗位（含挂靠部门）与职级；版本查看返回岗位/职级两个维度；
 * 回滚恢复主数据与岗位挂靠（不级联员工归属重排，MVP 局限）。部门维度沿用
 * org_dept_history 既有快照机制，不在此端点重复承载。权限复用 org:dept:*（不新增权限点）。</p>
 */
@RestController
@RequestMapping("/api/v1/org")
public class OrgVersionController {

    private final OrgVersionService versionService;

    public OrgVersionController(OrgVersionService versionService) {
        this.versionService = versionService;
    }

    /** 创建组织版本：全量快照全部岗位（含挂靠部门）与职级，返回版本号与两维度计数。 */
    @RequirePerm("org:dept:write")
    @PostMapping("/versions")
    public R<OrgVersionSummary> create() {
        return R.ok(versionService.create());
    }

    /** 版本列表（按创建先后倒序）。 */
    @RequirePerm("org:dept:read")
    @GetMapping("/versions")
    public R<List<OrgVersionSummary>> list() {
        return R.ok(versionService.list());
    }

    /** 版本查看：返回该版本的岗位/职级快照内容。 */
    @RequirePerm("org:dept:read")
    @GetMapping("/versions/{versionNo}")
    public R<OrgVersionDetail> get(@PathVariable String versionNo) {
        return R.ok(versionService.get(versionNo));
    }

    /** 版本回滚：按快照恢复岗位/职级主数据与岗位挂靠部门（MVP 不级联员工归属重排）。 */
    @RequirePerm("org:dept:write")
    @PostMapping("/versions/{versionNo}/rollback")
    public R<OrgVersionSummary> rollback(@PathVariable String versionNo) {
        return R.ok(versionService.rollback(versionNo));
    }
}
