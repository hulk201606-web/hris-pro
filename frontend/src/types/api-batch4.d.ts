/**
 * 二期攻坚批 4-8 接口类型契约（字段已对照后端 DTO 核实）。
 * 覆盖：站点域名映射（rcu site-domains）、候选人简历联动（rcu candidates）。
 */

/* ============================ 站点域名（rcu，批 7 #13） ============================ */

/** 域名状态：ENABLED 启用 / DISABLED 停用 */
export type SiteDomainStatus = 'ENABLED' | 'DISABLED';

/**
 * 官网租户域名映射（GET/POST /rcu/site-domains；PUT/DELETE /rcu/site-domains/{id}）。
 * domain 全局唯一（uk），小写、不含协议与端口；租户隔离由后端插件兜底。
 */
export interface SiteDomain {
  id: number;
  tenantId: number;
  domain: string;
  status: SiteDomainStatus | string;
  createdAt?: string;
  updatedAt?: string;
}

/* ============================ 候选人简历联动（rcu，批 6 #8） ============================ */

/** 候选人简历行（GET /rcu/candidates/{id}/resumes），对应后端 ResumeResponse */
export interface CandidateResume {
  id: number;
  candidateId: number;
  fileId?: number | null;
  parseResult?: string | null;
  createdAt?: string;
  updatedAt?: string;
}
