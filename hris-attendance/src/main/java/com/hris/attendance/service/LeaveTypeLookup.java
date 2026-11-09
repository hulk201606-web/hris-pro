package com.hris.attendance.service;

import com.hris.attendance.entity.LeaveLedger;
import com.hris.attendance.entity.LeaveQuota;
import com.hris.attendance.entity.LeaveType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假种批量装载工具（ATT-F-012/013）：把额度行/流水行涉及的假种一次查回，避免逐行查库。
 *
 * <p>从 LeaveQuotaService 拆分而来（单文件 ≤300 行门禁），LeaveTypeService 由调用方传入，
 * 宿主构造器签名不变。</p>
 */
final class LeaveTypeLookup {

    private LeaveTypeLookup() {
    }

    /** 按额度行涉及的假种装载。 */
    static Map<Long, LeaveType> forQuotas(LeaveTypeService leaveTypeService, List<LeaveQuota> quotas) {
        return byIds(leaveTypeService, quotas.stream().map(LeaveQuota::getLeaveTypeId).distinct().toList());
    }

    /** 按流水行涉及的假种装载。 */
    static Map<Long, LeaveType> forLedgers(LeaveTypeService leaveTypeService, List<LeaveLedger> ledgers) {
        return byIds(leaveTypeService, ledgers.stream().map(LeaveLedger::getLeaveTypeId).distinct().toList());
    }

    private static Map<Long, LeaveType> byIds(LeaveTypeService leaveTypeService, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, LeaveType> map = new LinkedHashMap<>();
        for (LeaveType t : leaveTypeService.listByIds(ids)) {
            map.put(t.getId(), t);
        }
        return map;
    }
}
