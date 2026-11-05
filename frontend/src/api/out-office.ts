/**
 * 外出/居家办公申请 API（ATT-F-018）。
 * 独立成文件的原因：attendance.ts 已达单行数门禁上限，本端点为独立业务能力，
 * 照 attendance-holiday.ts 的拆分范式单独维护。
 */
import { get, post } from './http';
import type { PageResult } from '../types/api';

/** 外出/居家申请记录（对齐 OutOfficeDtos.OutOfficeResponse） */
export interface OutOfficeItem {
  id: number;
  empId: number;
  /** OUT=外出 / WFH=居家办公 */
  type: string;
  startTime: string;
  endTime: string;
  reason: string | null;
  status: string;
  approverId: number | null;
  createdAt: string;
  updatedAt: string;
}

/** 自助创建请求体（empId 由后端取当前登录用户，前端不传） */
export interface OutOfficeCreateRequest {
  type: 'OUT' | 'WFH';
  startTime: string;
  endTime: string;
  reason?: string;
}

/** 我的申请列表（GET /att/out-offices/me） */
export function fetchMyOutOffices(): Promise<OutOfficeItem[]> {
  return get<OutOfficeItem[]>('/att/out-offices/me');
}

/** 自助创建（POST /att/out-offices） */
export function createOutOffice(data: OutOfficeCreateRequest): Promise<OutOfficeItem> {
  return post<OutOfficeItem>('/att/out-offices', data);
}

/** 管理端分页列表（GET /att/out-offices，可按员工/状态过滤） */
export function listOutOffices(params: {
  empId?: number;
  status?: string;
  page: number;
  size: number;
}): Promise<PageResult<OutOfficeItem>> {
  const query: Record<string, unknown> = { page: params.page, size: params.size };
  if (params.empId) query.empId = params.empId;
  if (params.status) query.status = params.status;
  return get<PageResult<OutOfficeItem>>('/att/out-offices', query);
}

/** 审批通过（POST /att/out-offices/{id}/approve），仅 PROCESSING 可审 */
export function approveOutOffice(id: number): Promise<OutOfficeItem> {
  return post<OutOfficeItem>(`/att/out-offices/${id}/approve`);
}

/** 审批驳回（POST /att/out-offices/{id}/reject），仅 PROCESSING 可审 */
export function rejectOutOffice(id: number): Promise<OutOfficeItem> {
  return post<OutOfficeItem>(`/att/out-offices/${id}/reject`);
}
