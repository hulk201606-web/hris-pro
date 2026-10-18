/**
 * 考勤模块静态 Mock（VITE_USE_MOCK=true 时使用）。
 * 配置类（组/班次/假种/周期）返回全量列表，操作类（日考勤/异常）返回分页。
 */
import type { PageResult, AttRequestQuery, FixCreateRequest, FixRequest, LeaveCreateRequest, LeaveRequest, OvertimeCreateRequest, OvertimeRequest } from '../types/api';
import type {
  AttException,
  AttGroup,
  AttLeaveType,
  AttPayrollCycle,
  AttQueryParams,
  AttShift,
  DailyAttendance,
  DailyComputeResult,
  ExceptionHandleResult,
} from '../types/admin';

function delay<T>(data: T, ms = 180): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms));
}

function now(): string {
  return new Date().toISOString().slice(0, 19).replace('T', ' ');
}

function paginate<T>(rows: T[], page: number, size: number): PageResult<T> {
  const start = (page - 1) * size;
  return { records: rows.slice(start, start + size), page, size, total: rows.length };
}

interface ListStore<T extends { id: number }> {
  list: () => Promise<T[]>;
  create: (data: any) => Promise<T>;
  update: (id: number, data: any) => Promise<T>;
}

function createListStore<T extends { id: number }>(seed: T[]): ListStore<T> {
  const rows: T[] = seed.map((r) => ({ ...r }));
  let seq = rows.reduce((max, r) => Math.max(max, r.id), 0);
  return {
    list: () => delay(rows.map((r) => ({ ...r }))),
    create: (data) => {
      const row = { ...data, id: ++seq, createdAt: now(), updatedAt: now() } as unknown as T;
      rows.unshift(row);
      return delay(row);
    },
    update: (id, data) => {
      const i = rows.findIndex((r) => r.id === id);
      const row = i >= 0 ? ({ ...rows[i], ...data, id, updatedAt: now() } as T) : (data as T);
      if (i >= 0) rows[i] = row;
      return delay(row);
    },
  };
}

const groupStore = createListStore<AttGroup>([
  { id: 1, name: '总部职能组', code: 'GRP-HQ', leaderEmpId: 200001, createdAt: '2026-06-01 09:00:00', updatedAt: '2026-06-01 09:00:00' },
  { id: 2, name: '研发中心', code: 'GRP-RD', leaderEmpId: 200002, createdAt: '2026-06-01 09:05:00', updatedAt: '2026-06-01 09:05:00' },
]);

const shiftStore = createListStore<AttShift>([
  { id: 1, name: '标准白班', code: 'SH-STD', startTime: '09:00:00', endTime: '18:00:00', flexible: 0, crossDay: false, createdAt: '2026-06-01 09:00:00', updatedAt: '2026-06-01 09:00:00' },
  { id: 2, name: '弹性班', code: 'SH-FLEX', startTime: '10:00:00', endTime: '19:00:00', flexible: 1, crossDay: false, createdAt: '2026-06-01 09:10:00', updatedAt: '2026-06-01 09:10:00' },
]);

const leaveTypeStore = createListStore<AttLeaveType>([
  { id: 1, name: '年假', code: 'ANNUAL', rules: '{"paid":true,"needProof":false,"minUnit":"0.5d","validMonths":12}', createdAt: '2026-06-01 09:00:00', updatedAt: '2026-06-01 09:00:00' },
  { id: 2, name: '事假', code: 'PERSONAL', rules: '{"paid":false,"needProof":false,"minUnit":"0.5d"}', createdAt: '2026-06-01 09:00:00', updatedAt: '2026-06-01 09:00:00' },
]);

const cycleStore = createListStore<AttPayrollCycle>([
  { id: 1, periodCode: '2026-09', startDate: '2026-09-01', endDate: '2026-09-30', status: 'CLOSED', createdAt: '2026-09-01 00:00:00', updatedAt: '2026-10-01 00:00:00' },
  { id: 2, periodCode: '2026-10', startDate: '2026-10-01', endDate: '2026-10-31', status: 'OPEN', createdAt: '2026-10-01 00:00:00', updatedAt: '2026-10-01 00:00:00' },
]);

const dailyRows: DailyAttendance[] = [
  { id: 1, empId: 200001, workDate: '2026-10-16', employmentId: 3001, attStatus: 'NORMAL', punchIn: '2026-10-16 08:58:00', punchOut: '2026-10-16 18:05:00', leaveHours: 0, overtimeHours: 0, updatedAt: '2026-10-16 18:10:00' },
  { id: 2, empId: 200001, workDate: '2026-10-15', employmentId: 3001, attStatus: 'LATE', punchIn: '2026-10-15 09:22:00', punchOut: '2026-10-15 18:02:00', leaveHours: 0, overtimeHours: 0, updatedAt: '2026-10-15 18:10:00' },
  { id: 3, empId: 200002, workDate: '2026-10-16', employmentId: 3002, attStatus: 'LEAVE', punchIn: '', punchOut: '', leaveHours: 8, overtimeHours: 0, updatedAt: '2026-10-16 18:10:00' },
  { id: 4, empId: 200002, workDate: '2026-10-15', employmentId: 3002, attStatus: 'ABSENT', punchIn: '', punchOut: '', leaveHours: 0, overtimeHours: 0, updatedAt: '2026-10-15 18:10:00' },
];

const exceptionRows: AttException[] = [
  { id: 1, empId: 200001, workDate: '2026-10-15', exceptionType: 'LATE', status: 'OPEN', description: '上班迟到 22 分钟', createdAt: '2026-10-15 09:25:00' },
  { id: 2, empId: 200002, workDate: '2026-10-15', exceptionType: 'ABSENT', status: 'OPEN', description: '全天缺卡', createdAt: '2026-10-15 18:10:00' },
  { id: 3, empId: 200001, workDate: '2026-10-14', exceptionType: 'MISSING_PUNCH', status: 'RESOLVED', description: '下班漏打卡，已补卡', createdAt: '2026-10-14 18:10:00' },
];

function listDaily(params: AttQueryParams, page: number, size: number): Promise<PageResult<DailyAttendance>> {
  const rows = dailyRows.filter((r) => {
    if (params.empId && r.empId !== params.empId) return false;
    if (params.attStatus && r.attStatus !== params.attStatus) return false;
    if (params.from && r.workDate < params.from) return false;
    if (params.to && r.workDate > params.to) return false;
    return true;
  });
  return delay(paginate(rows, page, size));
}

function computeDaily(workDate: string): Promise<DailyComputeResult> {
  return delay({
    workDate,
    employeeCount: 12,
    normalCount: 9,
    lateCount: 1,
    earlyCount: 0,
    absentCount: 1,
    leaveCount: 1,
    newExceptionCount: 2,
  });
}

function listExceptions(params: AttQueryParams, page: number, size: number): Promise<PageResult<AttException>> {
  const rows = exceptionRows.filter((r) => {
    if (params.empId && r.empId !== params.empId) return false;
    if (params.exceptionType && r.exceptionType !== params.exceptionType) return false;
    if (params.status && r.status !== params.status) return false;
    if (params.from && r.workDate < params.from) return false;
    if (params.to && r.workDate > params.to) return false;
    return true;
  });
  return delay(paginate(rows, page, size));
}

/* ---------- 审批请求单 mock ---------- */

const leaves: LeaveRequest[] = [
  { id: 1, empId: 200001, leaveTypeId: 1, leaveTypeName: '年假', bizKey: 'att_leave_0001', startTime: '2026-10-20 09:00:00', endTime: '2026-10-20 18:00:00', days: 1, reason: '家庭事务', status: 'PROCESSING', procInstId: 'pi-9001', createdAt: '2026-10-17 09:00:00', updatedAt: '2026-10-17 09:00:00' },
  { id: 2, empId: 200001, leaveTypeId: 2, leaveTypeName: '事假', bizKey: 'att_leave_0002', startTime: '2026-10-22 14:00:00', endTime: '2026-10-22 18:00:00', days: 0.5, reason: '就医', status: 'APPROVED', procInstId: 'pi-9002', createdAt: '2026-10-15 09:00:00', updatedAt: '2026-10-15 11:00:00' },
];

const overtimes: OvertimeRequest[] = [
  { id: 1, empId: 200001, bizKey: 'att_ot_0001', startTime: '2026-10-18 18:00:00', endTime: '2026-10-18 21:00:00', hours: 3, reason: '版本发布', mode: 'BEFORE', status: 'PROCESSING', procInstId: 'pi-9101', dayType: 'WORKDAY', rate: 1.5, createdAt: '2026-10-17 10:00:00', updatedAt: '2026-10-17 10:00:00' },
];

const fixes: FixRequest[] = [
  { id: 1, empId: 200001, bizKey: 'att_fix_0001', workDate: '2026-10-16', fixTime: '2026-10-16 18:05:00', type: 'OUT', reason: '下班忘记打卡', status: 'PROCESSING', procInstId: 'pi-9201', createdAt: '2026-10-16 18:30:00', updatedAt: '2026-10-16 18:30:00' },
];

function filterByRange<T extends { empId: number; status: string }>(
  rows: T[],
  params: AttRequestQuery,
  dateKey: keyof T & string,
): T[] {
  return rows.filter((r) => {
    if (params.empId && r.empId !== params.empId) return false;
    if (params.status && r.status !== params.status) return false;
    const d = String(r[dateKey]);
    if (params.from && d < params.from) return false;
    if (params.to && d > params.to) return false;
    return true;
  });
}

interface RequestStore {
  leaves: (params: AttRequestQuery, page: number, size: number) => Promise<PageResult<LeaveRequest>>;
  createLeave: (data: LeaveCreateRequest) => Promise<LeaveRequest>;
  overtimes: (params: AttRequestQuery, page: number, size: number) => Promise<PageResult<OvertimeRequest>>;
  createOvertime: (data: OvertimeCreateRequest) => Promise<OvertimeRequest>;
  fixes: (params: AttRequestQuery, page: number, size: number) => Promise<PageResult<FixRequest>>;
  createFix: (data: FixCreateRequest) => Promise<FixRequest>;
  setStatus: (kind: 'leaves' | 'overtimes' | 'fixes', id: number, status: string) => Promise<any>;
}

const requestStore: RequestStore = {
  leaves: (params, page, size) => delay(paginate(filterByRange(leaves, params, 'startTime'), page, size)),
  createLeave: (data) => {
    const row: LeaveRequest = {
      id: Date.now(),
      empId: data.empId,
      leaveTypeId: data.leaveTypeId,
      leaveTypeName: '年假',
      bizKey: `att_leave_${Date.now()}`,
      startTime: data.startTime,
      endTime: data.endTime,
      days: 1,
      reason: data.reason ?? '',
      status: 'PROCESSING',
      procInstId: `pi-${Date.now()}`,
      createdAt: now(),
      updatedAt: now(),
    };
    leaves.unshift(row);
    return delay(row);
  },
  overtimes: (params, page, size) => delay(paginate(filterByRange(overtimes, params, 'startTime'), page, size)),
  createOvertime: (data) => {
    const row: OvertimeRequest = {
      id: Date.now(),
      empId: data.empId,
      bizKey: `att_ot_${Date.now()}`,
      startTime: data.startTime,
      endTime: data.endTime,
      hours: 3,
      reason: data.reason ?? '',
      mode: data.mode ?? 'BEFORE',
      status: 'PROCESSING',
      procInstId: `pi-${Date.now()}`,
      dayType: 'WORKDAY',
      rate: 1.5,
      createdAt: now(),
      updatedAt: now(),
    };
    overtimes.unshift(row);
    return delay(row);
  },
  fixes: (params, page, size) => delay(paginate(filterByRange(fixes, params, 'workDate'), page, size)),
  createFix: (data) => {
    const row: FixRequest = {
      id: Date.now(),
      empId: data.empId,
      bizKey: `att_fix_${Date.now()}`,
      workDate: data.workDate,
      fixTime: data.fixTime,
      type: data.type,
      reason: data.reason ?? '',
      status: 'PROCESSING',
      procInstId: `pi-${Date.now()}`,
      createdAt: now(),
      updatedAt: now(),
    };
    fixes.unshift(row);
    return delay(row);
  },
  setStatus: (kind, id, status) => {
    const arr = kind === 'leaves' ? leaves : kind === 'overtimes' ? overtimes : fixes;
    const row = arr.find((r) => r.id === id);
    if (row) row.status = status;
    return delay(row);
  },
};

function handleException(id: number, action: string): Promise<ExceptionHandleResult> {
  return batchHandleExceptions([id], action);
}

function batchHandleExceptions(ids: number[], action: string): Promise<ExceptionHandleResult> {
  const failedIds: number[] = [];
  ids.forEach((id) => {
    const row = exceptionRows.find((r) => r.id === id);
    if (row) row.status = action === 'IGNORE' ? 'IGNORED' : 'RESOLVED';
    else failedIds.push(id);
  });
  return delay({ total: ids.length, success: ids.length - failedIds.length, failedIds });
}

export const attendanceMock = {
  listGroups: groupStore.list,
  createGroup: groupStore.create,
  updateGroup: groupStore.update,
  listShifts: shiftStore.list,
  createShift: shiftStore.create,
  updateShift: shiftStore.update,
  listLeaveTypes: leaveTypeStore.list,
  createLeaveType: leaveTypeStore.create,
  updateLeaveType: leaveTypeStore.update,
  listPayrollCycles: cycleStore.list,
  createPayrollCycle: cycleStore.create,
  updatePayrollCycle: cycleStore.update,
  closePayrollCycle: (id: number): Promise<AttPayrollCycle> => cycleStore.update(id, { status: 'CLOSED' }),
  listDaily,
  computeDaily,
  listExceptions,
  handleException,
  batchHandleExceptions,
  listLeaveRequests: requestStore.leaves,
  createLeaveRequest: requestStore.createLeave,
  approveLeaveRequest: (id: number) => requestStore.setStatus('leaves', id, 'APPROVED'),
  rejectLeaveRequest: (id: number) => requestStore.setStatus('leaves', id, 'REJECTED'),
  listOvertimeRequests: requestStore.overtimes,
  createOvertimeRequest: requestStore.createOvertime,
  approveOvertimeRequest: (id: number) => requestStore.setStatus('overtimes', id, 'APPROVED'),
  rejectOvertimeRequest: (id: number) => requestStore.setStatus('overtimes', id, 'REJECTED'),
  listFixRequests: requestStore.fixes,
  createFixRequest: requestStore.createFix,
  approveFixRequest: (id: number) => requestStore.setStatus('fixes', id, 'APPROVED'),
  rejectFixRequest: (id: number) => requestStore.setStatus('fixes', id, 'REJECTED'),
};
