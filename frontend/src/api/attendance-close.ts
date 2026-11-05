/**
 * 日考勤月结锁 API（批 11#19，后端 DailyCloseController）。
 * 权限点复用既有编码：写 att:closing:run（月结关账执行），读 att:group:read。
 * 期间格式 YYYY-MM；同租户同期间同时只能有一条 ACTIVE 锁。
 */
import { get, post } from './http';
import type { DailyCloseLock } from '../types/api';

/** 锁记录查询（GET /att/daily-close，active=true 只返回当前生效锁） */
export function listDailyCloseLocks(active?: boolean): Promise<DailyCloseLock[]> {
  return get<DailyCloseLock[]>('/att/daily-close', active === undefined ? undefined : { active });
}

/** 期间月结锁定（POST /att/daily-close/{period}/lock）：重复锁定后端返回业务错误 */
export function lockPeriod(period: string): Promise<DailyCloseLock> {
  return post<DailyCloseLock>(`/att/daily-close/${period}/lock`);
}

/** 期间解锁（POST /att/daily-close/{period}/unlock）：reason 必填并留审计 */
export function unlockPeriod(period: string, reason: string): Promise<DailyCloseLock> {
  return post<DailyCloseLock>(`/att/daily-close/${period}/unlock`, { reason });
}
