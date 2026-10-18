/**
 * 二期管理端接口类型契约（对齐 hris-recruit / compensation / performance / rag 的 DTO）。
 * 统一响应体解包后即为下列实体；分页 data 结构见 types/api.d.ts 的 PageResult。
 */
import type { PageResult } from './api';

/* ============================ 招聘（rcu） ============================ */

export interface Requisition {
  id: number;
  reqNo: string;
  deptId: number;
  positionId: number;
  headcount: number;
  reason: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Candidate {
  id: number;
  name: string;
  mobileCipher: string;
  mobileMask: string;
  email: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Offer {
  id: number;
  applicationId: number;
  offerNo: string;
  salaryBandCheck: number;
  salaryAmountCipher: string;
  salaryAmountMask: string;
  status: string;
  procInstId: string;
  createdAt: string;
  updatedAt: string;
}

/* ============================ 薪酬（cmp） ============================ */

export interface PayrollRun {
  id: number;
  periodCode: string;
  payGroupId: number;
  status: string;
  calcBy: number;
  reviewBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeePay {
  id: number;
  empId: number;
  payGroupId: number;
  payGradeId: number;
  effectiveFrom: string;
  effectiveTo: string;
  salaryAmountCipher: string;
  salaryAmountMask: string;
  bankAccountCipher: string;
  bankAccountMask: string;
  createdAt: string;
  updatedAt: string;
}

/** 算薪取数预览单行（跨域组合员工主档 + 考勤汇总） */
export interface PayrollPreviewLine {
  empId: number;
  empNo: string;
  name: string;
  deptId: number;
  attPeriodStatus: string;
  overtimeHours: number;
  leaveHours: number;
  periodEmployeeCount: number;
}

/* ============================ 绩效（prf） ============================ */

export interface Cycle {
  id: number;
  planId: number;
  periodCode: string;
  startDate: string;
  endDate: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Goal {
  id: number;
  cycleId: number;
  empId: number;
  title: string;
  weight: number;
  targetValue: string;
  resultValue: string;
  createdAt: string;
  updatedAt: string;
}

/* ============================ 知识库（kb） ============================ */

export interface KnowledgeSpace {
  id: number;
  name: string;
  code: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface KbDocument {
  id: number;
  spaceId: number;
  title: string;
  titleHash: string;
  docType: string;
  fileId: number;
  createdAt: string;
  updatedAt: string;
}

/** 员工档案简报（绩效上下文 / 知识范围跨域取数共用） */
export interface EmployeeProfileBrief {
  empId: number;
  name: string;
  empNo: string;
  status: number | string;
  tenantId: number;
  deptId: number | null;
  positionId: number | null;
  supervisorEmpId: number | null;
}

/* ============================ 通用 CRUD 组件契约 ============================ */

/** 通用行数据（宽索引，便于泛型实体向通用组件传递） */
export type CrudRow = Record<string, any>;

export interface CrudColumn {
  key: string;
  title: string;
  width?: number;
  /** 单元格渲染形态：文本/标签/日期/日期时间/时间/数值 */
  type?: 'text' | 'tag' | 'date' | 'datetime' | 'time' | 'number';
  /** 原值到展示文案的映射（如 0/1 → 固定/弹性） */
  valueMap?: Record<string, string>;
}

export interface CrudFieldOption {
  label: string;
  value: string | number;
}

export interface CrudField {
  key: string;
  label: string;
  type: 'text' | 'number' | 'date' | 'time' | 'select' | 'textarea';
  required?: boolean;
  options?: CrudFieldOption[];
  placeholder?: string;
  min?: number;
}

export interface CrudApi {
  list: (page: number, size: number) => Promise<PageResult<CrudRow>>;
  create?: (data: any) => Promise<unknown>;
  update?: (id: number, data: any) => Promise<unknown>;
  remove?: (id: number) => Promise<void>;
}

/** 非分页 CRUD 契约（考勤配置类接口返回 List，不提供删除） */
export interface SimpleCrudApi {
  list: () => Promise<CrudRow[]>;
  create?: (data: any) => Promise<unknown>;
  update?: (id: number, data: any) => Promise<unknown>;
}

/* ============================ 考勤（att） ============================ */

export interface AttGroup {
  id: number;
  name: string;
  code: string;
  leaderEmpId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface AttShift {
  id: number;
  name: string;
  code: string;
  startTime: string;
  endTime: string;
  flexible: number;
  crossDay: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AttLeaveType {
  id: number;
  name: string;
  code: string;
  rules: string;
  createdAt: string;
  updatedAt: string;
}

export interface AttPayrollCycle {
  id: number;
  periodCode: string;
  startDate: string;
  endDate: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface DailyAttendance {
  id: number;
  empId: number;
  workDate: string;
  employmentId: number;
  attStatus: string;
  punchIn: string;
  punchOut: string;
  leaveHours: number;
  overtimeHours: number;
  updatedAt: string;
}

export interface DailyComputeResult {
  workDate: string;
  employeeCount: number;
  normalCount: number;
  lateCount: number;
  earlyCount: number;
  absentCount: number;
  leaveCount: number;
  newExceptionCount: number;
}

export interface AttException {
  id: number;
  empId: number;
  workDate: string;
  exceptionType: string;
  status: string;
  description: string;
  createdAt: string;
}

export interface ExceptionHandleResult {
  total: number;
  success: number;
  failedIds: number[];
}

/** 日考勤 / 异常列表查询参数（可选筛选，后端分页） */
export interface AttQueryParams {
  empId?: number;
  from?: string;
  to?: string;
  attStatus?: string;
  exceptionType?: string;
  status?: string;
}
