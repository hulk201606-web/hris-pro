/**
 * 前后端共享接口类型契约 —— 员工档案模块
 */

export interface EmploymentInfo {
  legalEntityId: number;
  deptId: number;
  positionId: number;
  gradeId: number;
  hireType: string;
  supervisorEmpId: number;
  effectiveFrom: string;
}

export interface ProfileEducationDto {
  id: number;
  school: string;
  major: string;
  degree: string;
  startDate: string;
  endDate: string;
}

export interface ProfileWorkDto {
  id: number;
  company: string;
  positionName: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ProfileFamilyDto {
  id: number;
  name: string;
  relation: string;
  phone: string;
  company: string;
}

export interface ProfileEmergencyDto {
  id: number;
  name: string;
  relation: string;
  phone: string;
  address: string;
}

export interface EmployeeProfileResponse {
  id: number;
  empNo: string;
  name: string;
  gender: string;
  birthDate: string;
  email: string;
  /** 在职状态码 */
  status: number;
  statusLabel: string;
  blacklisted: number;
  /** 脱敏证件号（默认返回） */
  certNoMask: string;
  /** 脱敏手机号（默认返回） */
  mobileMask: string;
  /** 明文证件号（仅密域角色回填） */
  certNo?: string;
  /** 明文手机号（仅密域角色回填） */
  mobile?: string;
  employment?: EmploymentInfo;
  educations?: ProfileEducationDto[];
  works?: ProfileWorkDto[];
  families?: ProfileFamilyDto[];
  emergencies?: ProfileEmergencyDto[];
}

/* ---------------------------- 花名册与合同 ---------------------------- */

/** 花名册行（脱敏简报）：证件/手机号仅给掩码，明文不下发（EMP-F-025） */
export interface EmployeeBrief {
  id: number;
  empNo: string;
  name: string;
  gender?: string;
  email?: string;
  /** 证件号掩码，如 3101**********1234 */
  certNoMask?: string;
  /** 手机号掩码，如 138****5678 */
  mobileMask?: string;
  deptId?: number;
  positionId?: number;
  supervisorEmpId?: number;
  hireType?: string;
  status: number;
  statusLabel: string;
  blacklisted: boolean;
}

/** 合同台账 */
export interface ContractResponse {
  id: number;
  empId: number;
  contractNo?: string;
  /** 合同类型：固定期限 / 无固定期限 / 劳务 / 实习 */
  type: string;
  startDate: string;
  endDate?: string;
  /** 试用期结束日（用于转正提醒） */
  probationEnd?: string;
  status: string;
  /** 已续签次数，null 视为 0 */
  renewCnt?: number;
  fileId?: number;
}

/** 合同到期预警项 */
export interface ContractExpiringItem {
  id: number;
  empId: number;
  empNo: string;
  name: string;
  contractNo?: string;
  endDate: string;
  /** 剩余天数（<=0 表示已过期） */
  daysToExpire: number;
  /** 预警级别：LEVEL_7 / LEVEL_30 / LEVEL_60 */
  level: string;
}
