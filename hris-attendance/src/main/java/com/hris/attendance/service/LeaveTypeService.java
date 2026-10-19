package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.LeaveTypeDtos.LeaveTypeRequest;
import com.hris.attendance.dto.LeaveTypeDtos.LeaveTypeResponse;
import com.hris.attendance.entity.LeaveType;
import com.hris.attendance.mapper.LeaveTypeMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 假种配置（ATT-F-011）。年假/病假/事假/婚/产/育/丧/调休，按租户自定义规则。
 *
 * <p>rules 以 JSON 字符串保存（带薪、证明要求、最小单位、司龄阶梯、有效期、结转上限），
 * 折算公式不得硬编码在 Java 里（docs/03-03 §4）。</p>
 */
@Service
public class LeaveTypeService {

    private final LeaveTypeMapper leaveTypeMapper;

    public LeaveTypeService(LeaveTypeMapper leaveTypeMapper) {
        this.leaveTypeMapper = leaveTypeMapper;
    }

    /** 假种列表（按编码排序）。 */
    public List<LeaveTypeResponse> list() {
        return leaveTypeMapper.selectList(new LambdaQueryWrapper<LeaveType>()
                        .orderByAsc(LeaveType::getCode))
                .stream().map(this::toResponse).toList();
    }

    /** 单个假种；不存在抛 10002。 */
    public LeaveTypeResponse get(Long id) {
        return toResponse(require(id));
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeResponse create(LeaveTypeRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("lt");
        }
        ensureCodeAvailable(code, null);

        LeaveType type = new LeaveType();
        type.setId(IdWorker.getId());
        type.setName(req.name().trim());
        type.setCode(code);
        type.setRules(req.rules());
        leaveTypeMapper.insert(type);
        return toResponse(type);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeResponse update(Long id, LeaveTypeRequest req) {
        LeaveType type = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            type.setName(req.name().trim());
        }
        if (req.code() != null && !req.code().isBlank()) {
            String code = CodeUtil.normalize(req.code());
            ensureCodeAvailable(code, id);
            type.setCode(code);
        }
        if (req.rules() != null) {
            type.setRules(req.rules());
        }
        if (leaveTypeMapper.updateById(type) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(type);
    }

    /** 按 ID 批量加载假种（额度/流水响应填充假种名称，避免 N+1）。 */
    public List<LeaveType> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return leaveTypeMapper.selectBatchIds(ids);
    }

    /** 加载假种，不存在抛 10002（供请假单校验外键）。 */
    public LeaveType require(Long id) {
        LeaveType type = leaveTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "假种不存在");
        }
        return type;
    }

    private void ensureCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<LeaveType> qw = new LambdaQueryWrapper<LeaveType>()
                .eq(LeaveType::getCode, code);
        if (excludeId != null) {
            qw.ne(LeaveType::getId, excludeId);
        }
        Long cnt = leaveTypeMapper.selectCount(qw);
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "假种编码已存在");
        }
    }

    private LeaveTypeResponse toResponse(LeaveType t) {
        return new LeaveTypeResponse(t.getId(), t.getName(), t.getCode(), t.getRules(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
