/**
 * 排期批 9-12 前端契约（字段已逐项对照后端 DTO 核实）。
 * 覆盖：绩效周期流转/目标整组提交/评分掩码、薪酬环比复核报盘/调薪轨迹、
 *       招聘 Offer 发送回执/阶段流转/简历保留期、敏感词、流程附件与设计器。
 */

/* ============================ 绩效（prf） ============================ */

/** 周期四态（后端 CycleStatus，历史值由读侧归一） */
export type PrfCycleStatus = 'DRAFT' | 'ACTIVE' | 'SCORING' | 'CLOSED';

/** 目标整组提交条目（无 ID，整体替换语义） */
export interface GoalBatchItem {
  title: string;
  weight: number;
  targetValue?: string;
  resultValue?: string;
}

/** 目标整组提交（POST /prf/goals/batch，bizKey = cycleId:empId:dimension） */
export interface GoalBatchRequest {
  cycleId: number;
  empId: number;
  dimension?: string;
  items: GoalBatchItem[];
}

/** 评估任务（GET /prf/eval-tasks） */
export interface EvalTaskRow {
  id: number;
  cycleId: number;
  empId: number;
  evaluatorRole: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 评估结果（GET /prf/evaluations）。
 * scores/comment 为 null 表示**当前视角无权查看**（互见掩码），不代表未评分。
 */
export interface EvaluationRow {
  id: number;
  taskId: number | null;
  evaluatorId: number | null;
  scores: string | null;
  comment: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/* ============================ 薪酬（cmp） ============================ */

/** 单项薪资差异（diff = current - base） */
export interface PayrollItemDiff {
  code: string;
  base: number | null;
  current: number | null;
  diff: number | null;
}

/** 单员工差异（grossDiff 应发差 / netDiff 实发差 / changeRate 变化率） */
export interface PayrollEmployeeCompare {
  empId: number;
  items: PayrollItemDiff[];
  grossDiff: number | null;
  netDiff: number | null;
  changeRate: number | null;
}

/** 批次环比对比（GET /cmp/payroll-runs/{id}/compare） */
export interface PayrollCompareResult {
  runId: number;
  baseRunId: number | null;
  periodCode: string;
  basePeriodCode: string | null;
  employeeCount: number;
  grossDiff: number | null;
  netDiff: number | null;
  changeRate: number | null;
  employees: PayrollEmployeeCompare[];
  note: string | null;
}

/** 调薪轨迹（GET /cmp/employee-pays/{empId}/history） */
export interface EmployeePayHistoryItem {
  effectiveDate: string;
  baseAmount: number | null;
  reason: string | null;
  approvalId: number | null;
  createdBy: number | null;
  createdAt: string;
  /** PAY_VERSION 定薪版本链 / ADJUSTMENT 调整单补充 */
  source: 'PAY_VERSION' | 'ADJUSTMENT' | string;
}

/* ============================ 招聘（rcu） ============================ */

/** 发送 Offer 结果（status=FAILED 时 error 透出原因，Offer 保持 DRAFT） */
export interface OfferSendResult {
  offerId: number;
  logId: number | null;
  status: 'SENT' | 'FAILED' | string;
  error: string | null;
  offerStatus: string;
}

/** 投递记录（GET /rcu/applications） */
export interface ApplicationRow {
  id: number;
  candidateId: number;
  openingId: number;
  channelId: number | null;
  stage: string;
  status: string;
  reason: string | null;
  reasonNote: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** 阶段流转请求（REJECT 必填 reason，OTHER 必填 note；PASS 可带 tags） */
export interface ApplicationStageRequest {
  action: 'PASS' | 'REJECT';
  toStage?: string;
  reason?: string;
  note?: string;
  tags?: string[];
}

/** 简历行（GET /rcu/resumes） */
export interface ResumeRow {
  id: number;
  candidateId: number;
  fileId: number | null;
  parseResult: string | null;
  retentionUntil: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** 过期简历清理结果（cleaned = 本次逻辑删除条数） */
export interface ResumeCleanupResult {
  cleaned: number;
}

/* ============================ 知识库（kb） ============================ */

/** 敏感词（GET /kb/sensitive-words；level 仅 BLOCK/WARN） */
export interface SensitiveWordRow {
  id: number;
  word: string;
  level: 'BLOCK' | 'WARN' | string;
  createdAt?: string;
}

/** 知识切片（GET /kb/chunks；warnings 为敏感词 WARN 级命中词） */
export interface ChunkRow {
  id: number;
  docVersionId: number;
  spaceId: number;
  seq: number;
  headingPath: string | null;
  text: string | null;
  tokenCnt: number;
  aclTags: string | null;
  warnings: string[] | null;
  createdAt?: string;
  updatedAt?: string;
}

/* ============================ 流程（flow） ============================ */

/** 节点附件（GET /flow/tasks/{taskId}/attachments） */
export interface FlowAttachment {
  id: number;
  instanceId: string;
  taskId: string;
  fileId: string;
  fileName: string;
  uploadedBy: number | null;
  createdAt: string;
}

/** 结构化意见标签（tag: RECHECK_DATA/NEED_MORE_INFO/COMPLIANT/OTHER） */
export interface FlowOpinionTag {
  id: number;
  taskId: string;
  tag: string;
  comment: string | null;
  operator: number | null;
  createdAt: string;
}

/** BPMN XML 校验结果（valid=true 时 errors 为空） */
export interface ModelValidateResult {
  valid: boolean;
  errors: string[];
}

/** 部署结果（POST /flow/models/{id}/deploy） */
export interface FlowDeployResult {
  modelId: number;
  procDefKey: string;
  version: number;
  deploymentId: string;
}

/** 文件预签名上传响应（PLT） */
export interface PresignUploadResult {
  fileId: number;
  objectKey: string;
  uploadUrl: string;
  degraded: boolean;
}
