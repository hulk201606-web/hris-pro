/**
 * 法定节假日与调班日 API（ATT-F-021）。
 * 单独成文件的原因：考勤域主 API 文件已达行数门禁上限，日历相关端点
 * 是独立业务能力，拆开后两边都保持可读。
 */
import { del, get, post, put } from './http';

/** 日历记录：dayType 只允许 HOLIDAY（放假）/ WORKDAY（周末补班） */
export interface HolidayItem {
  id: number;
  holidayDate: string;
  name: string;
  dayType: string;
  remark: string | null;
}

/** 工作日判定：source 说明依据是日历配置还是星期推断 */
export interface WorkdayCheck {
  date: string;
  workday: boolean;
  source: string;
}

/** 按年份查询日历（GET /att/holidays，year 为空返回全部） */
export function listHolidays(year?: number): Promise<HolidayItem[]> {
  return get<HolidayItem[]>('/att/holidays', year ? { year } : undefined);
}

/** 判定某日是否工作日（GET /att/holidays/workday） */
export function checkWorkday(date: string): Promise<WorkdayCheck> {
  return get<WorkdayCheck>('/att/holidays/workday', { date });
}

export function createHoliday(data: Record<string, unknown>): Promise<HolidayItem> {
  return post<HolidayItem>('/att/holidays', data);
}

export function updateHoliday(id: number, data: Record<string, unknown>): Promise<HolidayItem> {
  return put<HolidayItem>(`/att/holidays/${id}`, data);
}

export function deleteHoliday(id: number): Promise<void> {
  return del<void>(`/att/holidays/${id}`);
}
