import { get } from './http';
import { isMockEnabled, mockApi } from './mock';
import type { EmployeeProfileResponse } from '../types/api';

/**
 * 我的信息（自助，本人数据，X-Self 语义）。
 * 后端以本人数据为准，此处以员工 ID 定位档案详情（GET /emp/employees/{id}）。
 */
export function fetchMyProfile(employeeId: number): Promise<EmployeeProfileResponse> {
  if (isMockEnabled()) return mockApi.myProfile();
  return get<EmployeeProfileResponse>(`/emp/employees/${employeeId}`);
}
