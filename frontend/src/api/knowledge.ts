/**
 * 知识库域 API 封装（后端 hris-rag 模块）。
 * 覆盖：知识空间（spaces）CRUD、文档（documents）CRUD、
 *       知识可见性范围取数（跨域拉员工档案简报用于检索前过滤）。
 */
import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { EmployeeProfileBrief, KbDocument, KnowledgeSpace } from '../types/admin';

/* ---------------------------- 知识空间 ---------------------------- */

/** 分页查询知识空间（GET /kb/spaces） */
export function listSpaces(page: number, size: number): Promise<PageResult<KnowledgeSpace>> {
  if (isMockEnabled()) return adminMock.listSpaces(page, size);
  return get<PageResult<KnowledgeSpace>>('/kb/spaces', { page, size });
}

/** 新建知识空间（POST /kb/spaces） */
export function createSpace(data: Record<string, unknown>): Promise<KnowledgeSpace> {
  if (isMockEnabled()) return adminMock.createSpace(data as any);
  return post<KnowledgeSpace>('/kb/spaces', data);
}

/** 更新知识空间（PUT /kb/spaces/{id}） */
export function updateSpace(id: number, data: Record<string, unknown>): Promise<KnowledgeSpace> {
  if (isMockEnabled()) return adminMock.updateSpace(id, data as any);
  return put<KnowledgeSpace>(`/kb/spaces/${id}`, data);
}

/** 删除知识空间（DELETE /kb/spaces/{id}） */
export function deleteSpace(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeSpace(id);
  return del<void>(`/kb/spaces/${id}`);
}

/* ---------------------------- 文档 ---------------------------- */

/** 分页查询文档列表（GET /kb/documents） */
export function listDocuments(page: number, size: number): Promise<PageResult<KbDocument>> {
  if (isMockEnabled()) return adminMock.listDocuments(page, size);
  return get<PageResult<KbDocument>>('/kb/documents', { page, size });
}

/** 新建文档（POST /kb/documents） */
export function createDocument(data: Record<string, unknown>): Promise<KbDocument> {
  if (isMockEnabled()) return adminMock.createDocument(data as any);
  return post<KbDocument>('/kb/documents', data);
}

/** 更新文档（PUT /kb/documents/{id}） */
export function updateDocument(id: number, data: Record<string, unknown>): Promise<KbDocument> {
  if (isMockEnabled()) return adminMock.updateDocument(id, data as any);
  return put<KbDocument>(`/kb/documents/${id}`, data);
}

/** 删除文档（DELETE /kb/documents/{id}） */
export function deleteDocument(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeDocument(id);
  return del<void>(`/kb/documents/${id}`);
}

/* ---------------------------- 知识范围（跨域） ---------------------------- */

/** 拉取员工档案简报，用于知识可见性范围判定 */
export function fetchKnowledgeScope(empId: number): Promise<EmployeeProfileBrief> {
  if (isMockEnabled()) return adminMock.knowledgeScope(empId);
  return get<EmployeeProfileBrief>('/kb/scope', { empId });
}
