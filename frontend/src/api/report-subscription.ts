/**
 * 报表订阅 API（PLT-F-005，后端 ReportSubscriptionController）。
 * 单独成文件：平台主 API 文件已接近行数门禁上限。
 * 启用订阅时后端即注册调度任务（task_key=RPT_SUB_<id>），停用仅下线任务不删记录。
 */
import { del, get, post, put } from './http';
import type { ReportSubscription, ReportSubscriptionRequest } from '../types/api';

/** 订阅列表（GET /plt/reports/subscriptions，需 plt:report:read） */
export function listReportSubscriptions(): Promise<ReportSubscription[]> {
  return get<ReportSubscription[]>('/plt/reports/subscriptions');
}

/** 新建订阅（POST /plt/reports/subscriptions，需 plt:report:write） */
export function createReportSubscription(
  data: ReportSubscriptionRequest,
): Promise<ReportSubscription> {
  return post<ReportSubscription>('/plt/reports/subscriptions', data);
}

/** 更新订阅（PUT /plt/reports/subscriptions/{id}），status 由 1→0 即下线调度 */
export function updateReportSubscription(
  id: number,
  data: ReportSubscriptionRequest,
): Promise<ReportSubscription> {
  return put<ReportSubscription>(`/plt/reports/subscriptions/${id}`, data);
}

/** 删除订阅（DELETE /plt/reports/subscriptions/{id}，逻辑删除并下线任务） */
export function deleteReportSubscription(id: number): Promise<void> {
  return del<void>(`/plt/reports/subscriptions/${id}`);
}
