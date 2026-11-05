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
import type { SensitiveWordRow, ChunkRow } from '../types/api-batch9';

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

/* ---------------------------- 敏感词（KBD-F-017） ---------------------------- */

/** 敏感词列表（GET /kb/sensitive-words）：复用 kb:document:read */
export function listSensitiveWords(): Promise<SensitiveWordRow[]> {
  return get<SensitiveWordRow[]>('/kb/sensitive-words');
}

/** 新增敏感词（POST /kb/sensitive-words）：level 缺省 WARN，仅 BLOCK/WARN */
export function createSensitiveWord(word: string, level: 'BLOCK' | 'WARN'): Promise<SensitiveWordRow> {
  return post<SensitiveWordRow>('/kb/sensitive-words', { word, level });
}

/** 删除敏感词（DELETE /kb/sensitive-words/{id}） */
export function deleteSensitiveWord(id: number): Promise<void> {
  return del<void>(`/kb/sensitive-words/${id}`);
}

/* ---------------------------- 知识切片（KBD-F-017） ---------------------------- */

/** 切片分页（GET /kb/chunks） */
export function listChunks(page: number, size: number): Promise<PageResult<ChunkRow>> {
  return get<PageResult<ChunkRow>>('/kb/chunks', { page, size });
}

/** 新建切片（POST /kb/chunks）：warnings 非空表示命中敏感词 WARN 级 */
export function createChunk(data: Record<string, unknown>): Promise<ChunkRow> {
  return post<ChunkRow>('/kb/chunks', data);
}

/** 删除切片（DELETE /kb/chunks/{id}） */
export function deleteChunk(id: number): Promise<void> {
  return del<void>(`/kb/chunks/${id}`);
}

/* ---------------------------- 知识范围（跨域） ---------------------------- */

/** 拉取员工档案简报，用于知识可见性范围判定 */
export function fetchKnowledgeScope(empId: number): Promise<EmployeeProfileBrief> {
  if (isMockEnabled()) return adminMock.knowledgeScope(empId);
  return get<EmployeeProfileBrief>('/kb/scope', { empId });
}

/* ---------------------------- 智能问答（KBD-F-008/009/010/011/012） ---------------------------- */

/** 引用项（citations 数组元素） */
export interface QaCitation {
  docId: number | null;
  title: string | null;
  chunkId: number;
  score: number;
}

/** 问答消息（拒答时 answerStatus = NO_RECALL） */
export interface QaMessage {
  id: number;
  sessionId: number;
  question: string | null;
  answer: string | null;
  citations: QaCitation[] | null;
  answerStatus: 'ANSWERED' | 'NO_RECALL' | null;
  latencyMs: number | null;
  model: string | null;
  createdAt: string;
}

/** 问答会话 */
export interface QaSession {
  id: number;
  userId: number;
  title: string | null;
  createdAt: string;
}

/** 创建问答会话（POST /kb/chat/sessions） */
export function createQaSession(data: { spaceId?: number; title?: string }): Promise<QaSession> {
  return post<QaSession>('/kb/chat/sessions', data);
}

/** 我的会话列表（GET /kb/chat/sessions） */
export function listQaSessions(): Promise<QaSession[]> {
  return get<QaSession[]>('/kb/chat/sessions');
}

/** 会话历史消息（GET /kb/chat/sessions/{id}/messages） */
export function listQaMessages(sessionId: number): Promise<QaMessage[]> {
  return get<QaMessage[]>(`/kb/chat/sessions/${sessionId}/messages`);
}

/** 提问并回答（POST /kb/chat/sessions/{id}/messages） */
export function askQa(sessionId: number, question: string): Promise<QaMessage> {
  return post<QaMessage>(`/kb/chat/sessions/${sessionId}/messages`, { question });
}

/** 消息反馈（POST /kb/chat/messages/{messageId}/feedback） */
export function feedbackQa(messageId: number, helpful: boolean, comment?: string): Promise<void> {
  return post<void>(`/kb/chat/messages/${messageId}/feedback`, { helpful, comment });
}
