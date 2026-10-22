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
