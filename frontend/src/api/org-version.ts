/**
 * 组织版本 API（批 8 #16 MVP / 批 11 #23 状态机，后端 OrgVersionController）。
 * 权限点复用 org:dept:*：列表/详情读 org:dept:read，创建/激活/回滚写 org:dept:write。
 * 列表 status 缺省只返回 ACTIVE；DRAFT 与 ALL 需要写权限（草稿仅管理端可见）。
 */
import { get, post } from './http';
import type { OrgVersion } from '../types/api';

/** 版本列表（GET /org/versions?status=ACTIVE|DRAFT|SUPERSEDED|ALL） */
export function listOrgVersions(status?: string): Promise<OrgVersion[]> {
  return get<OrgVersion[]>('/org/versions', status ? { status } : undefined);
}

/**
 * 新建版本（POST /org/versions）：全量快照岗位与职级，状态固定 DRAFT。
 * effectiveFrom 为空表示立即（后端按当天落库）。
 */
export function createOrgVersion(effectiveFrom?: string): Promise<OrgVersion> {
  return post<OrgVersion>('/org/versions', effectiveFrom ? { effectiveFrom } : {});
}

/** 手动激活版本（POST /org/versions/{versionNo}/activate）：原 ACTIVE 置 SUPERSEDED */
export function activateOrgVersion(versionNo: string): Promise<OrgVersion> {
  return post<OrgVersion>(`/org/versions/${versionNo}/activate`);
}

/** 回滚到历史版本（POST /org/versions/{versionNo}/rollback）：草稿不可回滚 */
export function rollbackOrgVersion(versionNo: string): Promise<OrgVersion> {
  return post<OrgVersion>(`/org/versions/${versionNo}/rollback`);
}
