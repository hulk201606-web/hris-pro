package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.flow.entity.FlowModel;
import com.hris.flow.entity.TimeoutCfg;
import com.hris.flow.mapper.FlowModelMapper;
import com.hris.flow.mapper.TimeoutCfgMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TaskQueryService 的任务视图填充工具（FLW-F-006/011）：批量加载表单 schema 与超时配置。
 *
 * <p>从 TaskQueryService 拆分而来（单文件 ≤300 行门禁），mapper 由调用方传入，
 * 因此宿主构造器签名保持不变。</p>
 */
final class TaskViewSupport {

    private TaskViewSupport() {
    }

    /** 按流程定义 key 批量取最新版模型的 form_schema_json（死代码激活：前端可渲染表单）。 */
    static Map<String, String> formSchemaByKeys(FlowModelMapper flowModelMapper, Set<String> keys) {
        Map<String, String> result = new HashMap<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            List<FlowModel> models = flowModelMapper.selectList(new LambdaQueryWrapper<FlowModel>()
                    .eq(FlowModel::getProcDefKey, key)
                    .orderByDesc(FlowModel::getVersion)
                    .last("limit 1"));
            if (models != null && !models.isEmpty() && models.get(0).getFormSchemaJson() != null) {
                result.put(key, models.get(0).getFormSchemaJson());
            }
        }
        return result;
    }

    /** 按流程定义 ID 批量取超时配置，key = procDefId#nodeId。 */
    static Map<String, TimeoutCfg> timeoutCfgByProcDefIds(TimeoutCfgMapper timeoutCfgMapper, List<String> procDefIds) {
        if (procDefIds.isEmpty()) {
            return Map.of();
        }
        List<TimeoutCfg> cfgs = timeoutCfgMapper.selectList(new LambdaQueryWrapper<TimeoutCfg>()
                .in(TimeoutCfg::getProcDefId, procDefIds));
        if (cfgs == null || cfgs.isEmpty()) {
            return Map.of();
        }
        return cfgs.stream()
                .filter(c -> c.getProcDefId() != null && c.getNodeId() != null)
                .collect(Collectors.toMap(c -> c.getProcDefId() + "#" + c.getNodeId(), c -> c, (a, b) -> a));
    }
}
