import { get, post, put } from './http';
import { isMockEnabled, mockApi } from './mock';
import type { PageResult } from '../types/api';
import type {
  ContractExpiringItem,
  ContractResponse,
  EmployeeBrief,
  EmployeeProfileResponse,
  RehireCheckResponse,
} from '../types/api';

/**
 * 员工域 API 封装（后端 hris-employee 模块）。
 * 覆盖：花名册多维筛选、档案详情与维护、黑名单、合同台账与到期预警。
 *
 * 注意：花名册返回脱敏简报（证件/手机号仅掩码），明文不下发（EMP-F-025）；
 * 敏感字段变更由服务端落 diff 审计并刷新掩码列（EMP-F-020）。
 */

/** 花名册筛选条件（均可选） */
export interface RosterQuery {
  name?: string;
  empNo?: string;
  status?: number;
  deptId?: number;
  positionId?: number;
  hireType?: string;
}

/** 我的信息（自助，本人数据）。后端以本人数据为准，以员工 ID 定位档案详情。 */
export function fetchMyProfile(employeeId: number): Promise<EmployeeProfileResponse> {
  if (isMockEnabled()) return mockApi.myProfile();
  return get<EmployeeProfileResponse>(`/emp/employees/${employeeId}`);
}

/** 花名册查询（GET /emp/employees）：多维筛选 + 分页（EMP-F-022） */
export function listRoster(
  page: number,
  size: number,
  filters: RosterQuery = {},
): Promise<PageResult<EmployeeBrief>> {
  return get<PageResult<EmployeeBrief>>('/emp/employees', { ...filters, page, size });
}

/** 档案详情（GET /emp/employees/{id}）：默认脱敏 */
export function getEmployee(id: number): Promise<EmployeeProfileResponse> {
  return get<EmployeeProfileResponse>(`/emp/employees/${id}`);
}

/** 手动建档（POST /emp/employees）：生成工号并初始化子档案 */
export function createEmployee(data: Record<string, unknown>): Promise<EmployeeProfileResponse> {
  return post<EmployeeProfileResponse>('/emp/employees', data);
}

/** 档案更新（PUT /emp/employees/{id}）：敏感字段变更落审计 */
export function updateEmployee(
  id: number,
  data: Record<string, unknown>,
): Promise<EmployeeProfileResponse> {
  return put<EmployeeProfileResponse>(`/emp/employees/${id}`, data);
}

/** 加入黑名单（POST /emp/employees/{id}/blacklist）：终态标记，落状态流水（EMP-F-016） */
export function blacklistEmployee(id: number, reason?: string): Promise<void> {
  const qs = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return post<void>(`/emp/employees/${id}/blacklist${qs}`);
}

/* ---------------------------- 合同 ---------------------------- */

/** 某员工的合同台账（GET /emp/contracts?empId=） */
export function listContracts(empId: number): Promise<ContractResponse[]> {
  return get<ContractResponse[]>('/emp/contracts', { empId });
}

/** 新签合同（POST /emp/contracts） */
export function createContract(data: Record<string, unknown>): Promise<ContractResponse> {
  return post<ContractResponse>('/emp/contracts', data);
}

/** 续签合同（PUT /emp/contracts/{id}/renew） */
export function renewContract(id: number, newEndDate: string): Promise<ContractResponse> {
  return put<ContractResponse>(`/emp/contracts/${id}/renew`, { newEndDate });
}

/** 合同到期预警（GET /emp/contracts/expiring）：未来 N 天内到期（EMP-F-015） */
export function listExpiringContracts(days = 60): Promise<ContractExpiringItem[]> {
  return get<ContractExpiringItem[]>('/emp/contracts/expiring', { days });
}

/* ---------------------------- 员工异动（生命周期办理） ---------------------------- */

/**
 * 发起入职办理（POST /emp/onboardings）。
 * 前置：员工状态必须为待入职（PENDING）；服务端会做编制校验，超编抛 51204。
 * 前端不预判，一律以服务端返回的错误码为准。
 */
export function submitOnboarding(data: Record<string, unknown>): Promise<unknown> {
  return post<unknown>('/emp/onboardings', data);
}

/** 发起转正办理（POST /emp/regularizations）。前置：状态为试用（PROBATION）。 */
export function submitRegularization(data: Record<string, unknown>): Promise<unknown> {
  return post<unknown>('/emp/regularizations', data);
}

/** 发起调动办理（POST /emp/transfers）。前置：状态为在职（ACTIVE）；目标部门/岗位做编制校验。 */
export function submitTransfer(data: Record<string, unknown>): Promise<unknown> {
  return post<unknown>('/emp/transfers', data);
}

/** 发起离职办理（POST /emp/resignations）。前置：状态为在职（ACTIVE）或试用（PROBATION）。 */
export function submitResignation(data: Record<string, unknown>): Promise<unknown> {
  return post<unknown>('/emp/resignations', data);
}

/* ---------------------------- 再入职识别 ---------------------------- */

/** 再入职识别（GET /emp/rehire-check）：按证件号查历史任职记录（EMP-F-029） */
export function checkRehire(certNo: string): Promise<RehireCheckResponse> {
  return get<RehireCheckResponse>('/emp/rehire-check', { certNo });
}
