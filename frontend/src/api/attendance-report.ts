/**
 * 考勤域报表 API（ATT-F-025）。自 attendance.ts 拆分以控制单文件行数。
 * 覆盖：出勤率 / 假期消耗 / 加班统计三个汇总报表。
 */
import { get } from './http';

/** 出勤率报表项：按员工汇总出勤天数与异常次数 */
export interface AttendanceRateItem {
  empId: number;
  workDays: number;
  normalDays: number;
  lateDays: number;
  earlyDays: number;
  absentDays: number;
  leaveDays: number;
  /** 出勤率（0-1） */
  attendanceRate: number;
}

/** 假期消耗报表项：按假种汇总额度与使用 */
export interface LeaveConsumeItem {
  leaveTypeId: number;
  leaveTypeName: string;
  totalQuota: number;
  usedQuota: number;
  /** 消耗率（0-1） */
  consumeRate: number;
}

/** 加班统计报表项：按员工汇总次数与时长 */
export interface OvertimeStatItem {
  empId: number;
  times: number;
  totalHours: number;
}

/** 出勤率报表（GET /att/reports/attendance-rate） */
export function getAttendanceRateReport(from: string, to: string): Promise<AttendanceRateItem[]> {
  return get<AttendanceRateItem[]>('/att/reports/attendance-rate', { from, to });
}

/** 假期消耗报表（GET /att/reports/leave-consume） */
export function getLeaveConsumeReport(period: string): Promise<LeaveConsumeItem[]> {
  return get<LeaveConsumeItem[]>('/att/reports/leave-consume', { period });
}

/** 加班统计报表（GET /att/reports/overtime） */
export function getOvertimeReport(from: string, to: string): Promise<OvertimeStatItem[]> {
  return get<OvertimeStatItem[]>('/att/reports/overtime', { from, to });
}
