/**
 * 前后端共享接口类型契约 —— 招聘模块的补充类型（hris-recruit）
 *
 * 说明：rcu 的基础 CRUD 类型在 types/admin.d.ts（二期代码生成时手写）。
 * 本文件只放**生成骨架之外**的手写业务类型（如查重），
 * 目的是纳入 check-type-sync.py 的字段一致性校验。
 *
 * 类型名与契约 schema 的对应关系（按「模块前缀 + 名称」匹配）：
 *   CandidateDedupResponse ↔ RcuCandidateDedupResponse
 *   CandidateDedupItem     ↔ RcuCandidateDedupItem
 */

/** 候选人查重的一条命中记录 */
export interface CandidateDedupItem {
  id: number;
  name: string;
  email?: string;
  /** 手机号掩码 */
  mobileMask?: string;
  status?: string;
  /** 命中维度：EMAIL（强信号，可视为同一人）/ NAME（弱信号，需人工确认） */
  matchField: string;
}

/** 候选人查重结果 */
export interface CandidateDedupResponse {
  duplicated: boolean;
  matchCount: number;
  matches: CandidateDedupItem[];
}
