/**
 * 前后端共享接口类型契约 —— 组织架构模块（hris-org）
 *
 * 类型名与契约 schema 的对应关系（check-type-sync.py 按「模块前缀 + 名称」匹配）：
 *   CatalogResponse  ↔ OrgCatalogResponse
 *   DeptNode         ↔ OrgDeptNode
 *   HeadcountUsage   ↔ OrgHeadcountUsage
 */

/** 目录类实体：法人主体 / 岗位 / 职位 / 职级 / 职级带 共用同一结构 */
export interface CatalogResponse {
  id: number;
  name: string;
  code: string;
  /** 启用状态：1=启用 0=停用 */
  status: number;
  /** 仅职级使用：所属职级带 ID */
  gradeBandId?: number;
}

/** 部门节点（树形，query(tree=true) 时 children 有值） */
export interface DeptNode {
  id: number;
  parentId: number;
  name: string;
  code: string;
  /** 物化路径，形如 1.3.7 */
  path: string;
  /** 部门负责人员工 ID */
  managerEmpId?: number;
  /** 生效区间（组织版本化） */
  effectiveFrom?: string;
  effectiveTo?: string;
  status: number;
  children?: DeptNode[];
}

/** 编制使用情况（部门 × 岗位 × 期间） */
export interface HeadcountUsage {
  deptId: number;
  positionId: number;
  period: string;
  quota: number;
  occupied: number;
  /** 空缺 = quota - occupied，可为负（超编） */
  vacancy: number;
  /** 占用率，quota<=0 时为 0 */
  usageRate: number;
}
