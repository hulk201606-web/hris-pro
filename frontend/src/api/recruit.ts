/**
 * 招聘域 API 封装（后端 hris-recruit 模块）。
 * 覆盖：职位需求（requisitions）/ 候选人（candidates）/ Offer（offers）CRUD，
 *       以及 Offer 录用建档（onboard，跨域联动员工预建档）。
 */
import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { CandidateDedupResponse } from '../types/api';
import type { Candidate, Offer, Requisition } from '../types/admin';
import type { ResumeParseResult } from '../types/api-batch2';
import type { CandidateResume, SiteDomain } from '../types/api-batch4';

/* ---------------------------- 职位需求 ---------------------------- */

/** 分页查询职位需求（GET /rcu/requisitions） */
export function listRequisitions(page: number, size: number): Promise<PageResult<Requisition>> {
  if (isMockEnabled()) return adminMock.listRequisitions(page, size);
  return get<PageResult<Requisition>>('/rcu/requisitions', { page, size });
}

/** 新建职位需求（POST /rcu/requisitions） */
export function createRequisition(data: Record<string, unknown>): Promise<Requisition> {
  if (isMockEnabled()) return adminMock.createRequisition(data as any);
  return post<Requisition>('/rcu/requisitions', data);
}

/** 更新职位需求（PUT /rcu/requisitions/{id}） */
export function updateRequisition(id: number, data: Record<string, unknown>): Promise<Requisition> {
  if (isMockEnabled()) return adminMock.updateRequisition(id, data as any);
  return put<Requisition>(`/rcu/requisitions/${id}`, data);
}

/** 删除职位需求（DELETE /rcu/requisitions/{id}） */
export function deleteRequisition(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeRequisition(id);
  return del<void>(`/rcu/requisitions/${id}`);
}

/** 发布到官网（POST /rcu/requisitions/{id}/publish）：OPEN 需求置 published=1 */
export function publishRequisition(id: number): Promise<Requisition> {
  return post<Requisition>(`/rcu/requisitions/${id}/publish`);
}

/** 撤下官网发布（POST /rcu/requisitions/{id}/unpublish） */
export function unpublishRequisition(id: number): Promise<Requisition> {
  return post<Requisition>(`/rcu/requisitions/${id}/unpublish`);
}

/* ---------------------------- 报表（RCS-F-020） ---------------------------- */

/** 漏斗报表行：stage 阶段 / count 去重候选人数 / conversion 相对 APPLIED 转化率（百分比，2 位小数） */
export interface FunnelItem {
  stage: string;
  count: number;
  conversion: number;
}

/** 招聘漏斗（GET /rcu/reports/funnel），from/to 为 ISO 日期时间，reqId 可选过滤 */
export function getFunnelReport(params: {
  from?: string;
  to?: string;
  reqId?: number;
}): Promise<FunnelItem[]> {
  const query: Record<string, unknown> = {};
  if (params.from) query.from = params.from;
  if (params.to) query.to = params.to;
  if (params.reqId) query.reqId = params.reqId;
  return get<FunnelItem[]>('/rcu/reports/funnel', query);
}

/* ---------------------------- 候选人 ---------------------------- */

/** 分页查询候选人（GET /rcu/candidates） */
export function listCandidates(page: number, size: number): Promise<PageResult<Candidate>> {
  if (isMockEnabled()) return adminMock.listCandidates(page, size);
  return get<PageResult<Candidate>>('/rcu/candidates', { page, size });
}

/** 新建候选人（POST /rcu/candidates） */
export function createCandidate(data: Record<string, unknown>): Promise<Candidate> {
  if (isMockEnabled()) return adminMock.createCandidate(data as any);
  return post<Candidate>('/rcu/candidates', data);
}

/** 更新候选人（PUT /rcu/candidates/{id}） */
export function updateCandidate(id: number, data: Record<string, unknown>): Promise<Candidate> {
  if (isMockEnabled()) return adminMock.updateCandidate(id, data as any);
  return put<Candidate>(`/rcu/candidates/${id}`, data);
}

/** 删除候选人（DELETE /rcu/candidates/{id}） */
export function deleteCandidate(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeCandidate(id);
  return del<void>(`/rcu/candidates/${id}`);
}

/* ---------------------------- Offer ---------------------------- */

/** 分页查询 Offer（GET /rcu/offers） */
export function listOffers(page: number, size: number): Promise<PageResult<Offer>> {
  if (isMockEnabled()) return adminMock.listOffers(page, size);
  return get<PageResult<Offer>>('/rcu/offers', { page, size });
}

/** 新建 Offer（POST /rcu/offers） */
export function createOffer(data: Record<string, unknown>): Promise<Offer> {
  if (isMockEnabled()) return adminMock.createOffer(data as any);
  return post<Offer>('/rcu/offers', data);
}

/** 更新 Offer（PUT /rcu/offers/{id}） */
export function updateOffer(id: number, data: Record<string, unknown>): Promise<Offer> {
  if (isMockEnabled()) return adminMock.updateOffer(id, data as any);
  return put<Offer>(`/rcu/offers/${id}`, data);
}

/** 删除 Offer（DELETE /rcu/offers/{id}） */
export function deleteOffer(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeOffer(id);
  return del<void>(`/rcu/offers/${id}`);
}

/** 录用建档：Offer 转员工预建档（跨域联动），返回新员工 ID */
export function onboardOffer(id: number): Promise<number> {
  if (isMockEnabled()) return adminMock.onboardOffer(id);
  return post<number>(`/rcu/offers/${id}/onboard`);
}

/* ---------------------------- 简历解析（二期批 2） ---------------------------- */

/**
 * 查询简历解析结果（GET /rcu/resumes/{id}/parse）。
 * parseStatus 为 RULE_LIMITED 时结果仅供参考，需人工核对。
 */
export function getResumeParse(resumeId: number): Promise<ResumeParseResult> {
  return get<ResumeParseResult>(`/rcu/resumes/${resumeId}/parse`);
}

/** 触发规则解析（POST /rcu/resumes/{id}/parse，缺省 mode=rule）：幂等重跑，返回最新解析结果 */
export function parseResume(resumeId: number): Promise<ResumeParseResult> {
  return post<ResumeParseResult>(`/rcu/resumes/${resumeId}/parse`);
}

/** AI 解析（POST /rcu/resumes/{id}/parse?mode=ai，批 6 #8）：LLM 结构化抽取，parseStatus=AI_PARSED */
export function parseResumeAi(resumeId: number): Promise<ResumeParseResult> {
  return post<ResumeParseResult>(`/rcu/resumes/${resumeId}/parse?mode=ai`);
}

/** 候选人简历列表（GET /rcu/candidates/{id}/resumes，批 6 #8）：存在但无简历返回空数组 */
export function listCandidateResumes(candidateId: number): Promise<CandidateResume[]> {
  return get<CandidateResume[]>(`/rcu/candidates/${candidateId}/resumes`);
}

/** 候选人最新解析结果（GET /rcu/candidates/{id}/resume-parse）：无简历/无解析时后端 NOT_FOUND */
export function getCandidateResumeParse(candidateId: number): Promise<ResumeParseResult> {
  return get<ResumeParseResult>(`/rcu/candidates/${candidateId}/resume-parse`);
}

/* ---------------------------- 站点域名（批 7 #13） ---------------------------- */

/** 分页查询站点域名映射（GET /rcu/site-domains，租户隔离） */
export function listSiteDomains(page: number, size: number): Promise<PageResult<SiteDomain>> {
  return get<PageResult<SiteDomain>>('/rcu/site-domains', { page, size });
}

/** 新建站点域名映射（POST /rcu/site-domains）：domain 全局唯一，冲突转参数错误 */
export function createSiteDomain(data: Record<string, unknown>): Promise<SiteDomain> {
  return post<SiteDomain>('/rcu/site-domains', data);
}

/** 更新站点域名映射（PUT /rcu/site-domains/{id}）：可改域名与状态，未传字段不更新 */
export function updateSiteDomain(id: number, data: Record<string, unknown>): Promise<SiteDomain> {
  return put<SiteDomain>(`/rcu/site-domains/${id}`, data);
}

/** 删除站点域名映射（DELETE /rcu/site-domains/{id}，逻辑删除） */
export function deleteSiteDomain(id: number): Promise<void> {
  return del<void>(`/rcu/site-domains/${id}`);
}

/** 人工修正解析结果（PUT /rcu/resumes/{id}/parse）：覆盖 parsed 三字段与教育/工作经历 */
export function updateResumeParse(
  resumeId: number,
  data: Record<string, unknown>,
): Promise<ResumeParseResult> {
  return put<ResumeParseResult>(`/rcu/resumes/${resumeId}/parse`, data);
}

/* ---------------------------- 候选人查重（RCS-F-009） ---------------------------- */

/**
 * 候选人查重（GET /rcu/candidate-dedup）：简历入库前检查是否已有同人。
 *
 * 匹配维度：email 精确（强信号）、name 辅助（弱信号，同名不必然同人）。
 * 手机号暂不纳入——该列由调用方传入 mobileCipher 后原样落库，
 * 若上游用随机 IV 加密则同号密文不同，等值查询不可靠。
 */
export function checkCandidateDuplicate(
  email?: string,
  name?: string,
): Promise<CandidateDedupResponse> {
  const params: Record<string, unknown> = {};
  if (email) params.email = email;
  if (name) params.name = name;
  return get<CandidateDedupResponse>('/rcu/candidate-dedup', params);
}
