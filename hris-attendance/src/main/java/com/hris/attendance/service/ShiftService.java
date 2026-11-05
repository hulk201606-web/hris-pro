package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.ShiftDtos.ShiftRequest;
import com.hris.attendance.dto.ShiftDtos.ShiftResponse;
import com.hris.attendance.entity.Shift;
import com.hris.attendance.mapper.ShiftMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 班次（ATT-F-002）。固定/弹性/跨天班次。
 *
 * <p>跨天语义（B-5）：att_shift.cross_day 显式标志（1=下班时间为次日）；
 * 未传时按 end_time&lt;=start_time 派生兜底，兼容历史数据与旧调用方。</p>
 */
@Service
public class ShiftService {

    private final ShiftMapper shiftMapper;

    public ShiftService(ShiftMapper shiftMapper) {
        this.shiftMapper = shiftMapper;
    }

    /** 班次列表（按编码排序）。 */
    public List<ShiftResponse> list() {
        return shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                        .orderByAsc(Shift::getCode))
                .stream().map(this::toResponse).toList();
    }

    /** 单个班次；不存在抛 10002。 */
    public ShiftResponse get(Long id) {
        return toResponse(require(id));
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse create(ShiftRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("as");
        }
        ensureCodeAvailable(code, null);

        Shift shift = new Shift();
        shift.setId(IdWorker.getId());
        shift.setName(req.name().trim());
        shift.setCode(code);
        shift.setStartTime(req.startTime());
        shift.setEndTime(req.endTime());
        shift.setFlexible(req.flexible() == null ? 0 : req.flexible());
        shift.setCrossDay(resolveCrossDay(req.crossDay(), req.startTime(), req.endTime()));
        shiftMapper.insert(shift);
        return toResponse(shift);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public ShiftResponse update(Long id, ShiftRequest req) {
        Shift shift = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            shift.setName(req.name().trim());
        }
        if (req.code() != null && !req.code().isBlank()) {
            String code = CodeUtil.normalize(req.code());
            ensureCodeAvailable(code, id);
            shift.setCode(code);
        }
        boolean timeChanged = false;
        if (req.startTime() != null) {
            shift.setStartTime(req.startTime());
            timeChanged = true;
        }
        if (req.endTime() != null) {
            shift.setEndTime(req.endTime());
            timeChanged = true;
        }
        if (req.flexible() != null) {
            shift.setFlexible(req.flexible());
        }
        if (req.crossDay() != null) {
            shift.setCrossDay(req.crossDay());
        } else if (timeChanged) {
            shift.setCrossDay(resolveCrossDay(null, shift.getStartTime(), shift.getEndTime()));
        }
        if (shiftMapper.updateById(shift) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(shift);
    }

    /** 加载班次，不存在抛 10002（供其他 service 校验外键与比对时刻）。 */
    public Shift require(Long id) {
        Shift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "班次不存在");
        }
        return shift;
    }

    /** 按 ID 批量加载班次（排班结果填充班次快照，避免 N+1）。 */
    public List<Shift> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return shiftMapper.selectBatchIds(ids);
    }

    private void ensureCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<Shift> qw = new LambdaQueryWrapper<Shift>().eq(Shift::getCode, code);
        if (excludeId != null) {
            qw.ne(Shift::getId, excludeId);
        }
        Long cnt = shiftMapper.selectCount(qw);
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "班次编码已存在");
        }
    }

    /** 跨天标志解析：显式传入优先，否则按 end_time<=start_time 派生。 */
    private Integer resolveCrossDay(Integer crossDay, java.time.LocalTime start,
                                    java.time.LocalTime end) {
        if (crossDay != null) {
            return crossDay;
        }
        return start != null && end != null && !end.isAfter(start) ? 1 : 0;
    }

    private ShiftResponse toResponse(Shift s) {
        return new ShiftResponse(s.getId(), s.getName(), s.getCode(), s.getStartTime(),
                s.getEndTime(), s.getFlexible(), AttendanceCalculator.isCrossDay(s),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
