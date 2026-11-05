package com.hris.compensation.controller;

import com.hris.compensation.dto.CalcTaskDtos.FormulaValidateRequest;
import com.hris.compensation.dto.CalcTaskDtos.FormulaValidateResponse;
import com.hris.compensation.service.FormulaSandboxService;
import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

/**
 * 算薪公式沙箱端点（CMP-F-021）。
 *
 * <p>公式保存前可先 validate：Aviator 编译校验（语法错误返回具体位置信息）
 * + 样例变量 dry-run 求值；响应携带变量/函数白名单清单供前端提示。</p>
 */
@RestController
@RequestMapping("/api/v1/cmp/formulas")
public class FormulaController {

    private final FormulaSandboxService sandbox;

    public FormulaController(FormulaSandboxService sandbox) {
        this.sandbox = sandbox;
    }

    /** 公式校验（编译 + dry-run；权限复用 payroll write——公式变更属算薪配置写路径）。 */
    @RequirePerm("cmp:payroll-run:write")
    @PostMapping("/validate")
    public R<FormulaValidateResponse> validate(@Valid @RequestBody ValidateRequest body) {
        FormulaSandboxService.ValidateResult r =
                sandbox.validate(body.formula(), body.sampleVars());
        return R.ok(new FormulaValidateResponse(r.valid(), r.message(), r.result(),
                new ArrayList<>(r.variables()), new ArrayList<>(r.functions()),
                new ArrayList<>(r.baseVariables())));
    }

    /** 请求体（顶层校验注解：formula 必填）。 */
    public record ValidateRequest(@NotBlank(message = "formula 不能为空") String formula,
                                  java.util.Map<String, Object> sampleVars) {
    }
}
