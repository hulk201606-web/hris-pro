package com.hris.recruit.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseRequest;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseResponse;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseUpdateRequest;
import com.hris.recruit.service.ResumeParseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历解析端点（RCS-F-007）：触发规则解析 / 查询最新解析 / 人工修正。
 *
 * <p>业务域：招聘流程（recruit）；分层：Controller。权限复用 resume write/read 码。</p>
 */
@RestController
@RequestMapping("/api/v1/rcu/resumes/{resumeId}/parse")
public class ResumeParseController {

    private final ResumeParseService service;

    public ResumeParseController(ResumeParseService service) {
        this.service = service;
    }

    /** 触发解析（写权限，复用 resume write 码）；重复调用幂等覆盖旧结果。 */
    @RequirePerm("rcu:resume:write")
    @PostMapping
    public R<ResumeParseResponse> parse(@PathVariable Long resumeId,
                                        @RequestBody(required = false) ResumeParseRequest req) {
        return R.ok(service.parse(resumeId, req));
    }

    /** 查询最新解析结果（读权限，复用 resume read 码）。 */
    @RequirePerm("rcu:resume:read")
    @GetMapping
    public R<ResumeParseResponse> get(@PathVariable Long resumeId) {
        return R.ok(service.get(resumeId));
    }

    /** 人工修正（写权限，复用 resume write 码）；parse_status 保持不变，updated_by 留痕。 */
    @RequirePerm("rcu:resume:write")
    @PutMapping
    public R<ResumeParseResponse> update(@PathVariable Long resumeId,
                                         @RequestBody ResumeParseUpdateRequest req) {
        return R.ok(service.update(resumeId, req));
    }
}
