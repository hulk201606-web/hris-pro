/**
 * 二期攻坚批 3 接口类型契约（字段已对照后端 DTO 核实）。
 * 覆盖：薪资项（cmp pay-items）、算薪执行/监控（cmp calc）、公式沙箱（cmp formulas）、
 *       多法人成本报表（cmp reports）、人力结构分析（org reports）。
 */

/* ============================ 薪资项（cmp） ============================ */

/** 薪资项类别：应发项 / 扣款项 / 个税 / 净额 */
export type PayItemCategory = 'EARNING' | 'DEDUCTION' | 'TAX' | 'NET';

/** 薪资项（GET/POST/PUT /cmp/pay-items；formula 为 Aviator 沙箱表达式，最长 512 字符） */
export interface PayItem {
  id: number;
  payGroupId: number;
  name: string;
  code: string;
  category: PayItemCategory | string;
  seq?: number | null;
  formula?: string | null;
  /** 1 启用 / 0 停用（创建必填） */
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 薪酬组（GET /cmp/pay-groups，薪资项过滤下拉用） */
export interface PayGroup {
  id: number;
  name: string;
  code: string;
  payCycle: string;
  createdAt?: string;
  updatedAt?: string;
}

/* ============================ 算薪执行与监控（cmp） ============================ */

/** 触发算薪响应（POST /cmp/payroll-runs/{id}/calculate，缺省异步：返回 CALCULATING 快照，全 0 进度） */
export interface CalcRunResponse {
  runId: number;
  status: string;
  total: number;
  success: number;
  failed: number;
  skipped: number;
}

/** 逐员工算薪任务行（GET /cmp/payroll-runs/{id}/calc-tasks） */
export interface CalcTask {
  id: number;
  runId: number;
  empId: number;
  status: 'SUCCESS' | 'FAILED' | 'SKIPPED' | string;
  error?: string | null;
  /** 计算结果明细 JSON（逐薪资项金额） */
  itemsJson?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** 计算进度（GET /cmp/payroll-runs/{id}/calc-progress） */
export interface CalcProgress {
  runId: number;
  total: number;
  success: number;
  failed: number;
  skipped: number;
  /** 0-100 */
  progressPercent: number;
}

/* ============================ 公式沙箱（cmp） ============================ */

/** 公式校验响应（POST /cmp/formulas/validate：编译校验 + 试算 + 白名单清单） */
export interface FormulaValidateResponse {
  valid: boolean;
  message: string;
  /** 试算结果（BigDecimal 字符串；失败时为 null） */
  result?: string | null;
  /** 表达式引用到的变量 */
  variables: string[];
  /** 函数白名单，如 round(x,n) */
  functions: string[];
  /** 基础变量白名单：base/lateCount/absentDays */
  baseVariables: string[];
}

/* ============================ 多法人成本报表（cmp） ============================ */

/** 法人维度成本合并行（GET /cmp/reports/legal-entity-cost；taxTotal 恒为 null 待补齐） */
export interface LegalEntityCostRow {
  legalEntityId: number;
  legalEntityName: string;
  headcount: number;
  grossTotal?: string | number | null;
  deductionTotal?: string | number | null;
  taxTotal?: string | number | null;
  netTotal?: string | number | null;
}

/* ============================ 人力结构分析（org） ============================ */

/** 部门维度计数项（deptScope=top 为一级部门滚动聚合，all 逐部门） */
export interface DeptCount {
  deptId: number;
  deptName: string;
  count: number;
}

/** 雇佣类型维度计数项：FULLTIME/PARTTIME/INTERN/CONSULTANT */
export interface HireTypeCount {
  hireType: string;
  count: number;
}

/** 在职状态维度计数项（emp_employee.status 码） */
export interface StatusCount {
  status: number;
  count: number;
}

/** 人力结构分析响应（GET /org/reports/headcount-structure） */
export interface HeadcountStructureResponse {
  byDept: DeptCount[];
  byHireType: HireTypeCount[];
  byStatus: StatusCount[];
}
