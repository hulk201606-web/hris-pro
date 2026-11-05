/**
 * 二期攻坚批 2 接口类型契约（契约由 lead 锁定，后端并行开发中）。
 * 覆盖：简历解析（rcu）、面谈记录（prf）、期间关账（cmp）、合同条款（emp）。
 */

/* ============================ 简历解析（rcu） ============================ */

/** 解析状态：OK 规则解析成功；RULE_LIMITED 命中规则上限，结果仅供参考；AI_PARSED 大模型解析成功（批 6） */
export type ResumeParseStatus = 'OK' | 'RULE_LIMITED' | 'AI_PARSED';

/** 解析出的教育经历条目 */
export interface ResumeParseEducation {
  school?: string;
  major?: string;
  degree?: string;
  startDate?: string;
  endDate?: string;
}

/** 解析出的工作经历条目 */
export interface ResumeParseExperience {
  company?: string;
  positionName?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
}

/**
 * 简历解析结果（GET/POST/PUT /rcu/resumes/{id}/parse）。
 * educationJson / experienceJson 为 JSON 数组（后端可能以字符串或数组下发，
 * 前端展示层统一做兜底解析）。
 */
export interface ResumeParseResult {
  id?: number;
  resumeId?: number;
  candidateId?: number;
  parsedName?: string;
  parsedMobile?: string;
  parsedEmail?: string;
  educationJson?: string | ResumeParseEducation[] | null;
  experienceJson?: string | ResumeParseExperience[] | null;
  parseStatus?: ResumeParseStatus | string;
  parseError?: string | null;
}

/* ============================ 面谈记录（prf） ============================ */

/** 面谈结论：改进 / 维持 / 晋升 / 绩效改进计划 */
export type InterviewConclusion = 'IMPROVE' | 'KEEP' | 'PROMOTE' | 'PIP';

/** PIP 跟进状态 */
export type PipStatus = 'ONGOING' | 'PASSED' | 'FAILED';

/** 面谈记录（GET /prf/interviews 分页行；POST/PUT 请求体同字段） */
export interface InterviewRecord {
  id: number;
  cycleId: number;
  empId: number;
  /** 面谈轮次 */
  round: number;
  interviewDate: string;
  interviewerEmpId: number;
  summary: string;
  conclusion: InterviewConclusion | string;
  /** conclusion 为 PIP 时必填 */
  pipStartDate?: string | null;
  pipEndDate?: string | null;
  /** PIP 跟进状态，仅 conclusion 为 PIP 时有意义 */
  pipStatus?: PipStatus | string | null;
  createdAt?: string;
  updatedAt?: string;
}

/* ============================ 期间关账（cmp） ============================ */

/** 关账状态：ACTIVE 已关账（锁定中）；RELEASED 已解锁 */
export type PeriodLockStatus = 'ACTIVE' | 'RELEASED';

/** 期间关账记录（GET /cmp/period-locks） */
export interface PeriodLock {
  id: number;
  periodCode: string;
  status: PeriodLockStatus | string;
  lockedBy: number;
  lockedAt: string;
  releasedBy?: number | null;
  releasedAt?: string | null;
  /** 解锁原因（解锁时必填） */
  unlockReason?: string | null;
}

/* ============================ 合同条款（emp） ============================ */

/** 条款类型 */
export type ContractTermType =
  | 'PROBATION'
  | 'SALARY'
  | 'CONFIDENTIALITY'
  | 'NON_COMPETE'
  | 'CUSTOM';

/** 合同条款（GET/POST /emp/contracts/{id}/terms；PUT/DELETE /terms/{termId}） */
export interface ContractTerm {
  id: number;
  contractId: number;
  termType: ContractTermType | string;
  content: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  createdAt?: string;
  updatedAt?: string;
}
