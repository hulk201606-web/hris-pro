package com.hris.flow.strategy;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 策略工厂（FLW-F-008）：按 {@link ApprovalStrategyType} 取实现，新增策略只需注册实现类。
 */
@Component
public class ApprovalStrategyFactory {

    private final Map<ApprovalStrategyType, ApprovalStrategy> registry = new EnumMap<>(ApprovalStrategyType.class);

    public ApprovalStrategyFactory(List<ApprovalStrategy> strategies) {
        for (ApprovalStrategy s : strategies) {
            registry.put(s.type(), s);
        }
    }

    /** 取策略；未注册视为配置错误（仅显式配置 strategy 的节点会走到这里）。 */
    public ApprovalStrategy require(ApprovalStrategyType type) {
        ApprovalStrategy s = registry.get(type);
        if (s == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未注册的会签策略: " + type);
        }
        return s;
    }

    public Optional<ApprovalStrategy> find(ApprovalStrategyType type) {
        return Optional.ofNullable(registry.get(type));
    }
}
