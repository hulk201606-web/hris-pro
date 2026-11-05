package com.hris.performance.controller;

import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.security.AuthContextHolder;
import com.hris.common.security.RequirePerm;
import com.hris.performance.dto.InterviewDtos.InterviewCreateRequest;
import com.hris.performance.dto.InterviewDtos.InterviewResponse;
import com.hris.performance.dto.InterviewDtos.InterviewUpdateRequest;
import com.hris.performance.dto.InterviewDtos.PipResultRequest;
import com.hris.performance.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview 端点（PRF-F-008：绩效面谈记录与 PIP 跟踪）。
 */
@RestController
@RequestMapping("/api/v1/prf/interviews")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    /** 分页查询：cycleId/empId 双可选过滤。权限点见 @RequirePerm。 */
    @RequirePerm("prf:interview:read")
    @GetMapping
    public R<PageResult<InterviewResponse>> list(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) Long empId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(cycleId, empId, page, size));
    }

    /** 自助视图：本人面谈记录（self 语义，empId 取网关透传的当前用户 ID）。 */
    @GetMapping("/me")
    public R<PageResult<InterviewResponse>> me(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.listMine(currentEmpId(), page, size));
    }

    /** 新建面谈记录：PIP 必填项与 round 唯一由服务端校验。权限点见 @RequirePerm。 */
    @RequirePerm("prf:interview:write")
    @PostMapping
    public R<InterviewResponse> create(@Valid @RequestBody InterviewCreateRequest req) {
        return R.ok(service.create(req));
    }

    /** 更新面谈记录：pip_status 旁路封死，只经 pip-result 迁移。权限点见 @RequirePerm。 */
    @RequirePerm("prf:interview:write")
    @PutMapping("/{id}")
    public R<InterviewResponse> update(@PathVariable Long id, @Valid @RequestBody InterviewUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    /** PIP 结果登记：仅 ONGOING 可迁移为 PASSED/FAILED。权限点见 @RequirePerm。 */
    @RequirePerm("prf:interview:write")
    @PostMapping("/{id}/pip-result")
    public R<InterviewResponse> pipResult(@PathVariable Long id, @Valid @RequestBody PipResultRequest req) {
        return R.ok(service.pipResult(id, req));
    }

    /** userId→empId 解析范式（照 leave-quotas/me）：MVP 两个 ID 同源。 */
    private Long currentEmpId() {
        Long userId = AuthContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        return userId;
    }
}
