/**
 * 考勤围栏与考勤机设备 API（批 12#27，后端 LocationController / DeviceController）。
 * 权限点复用考勤组读写码：读 att:group:read，写 att:group:write。
 * 设备密钥仅在新增时提交，后端只存摘要，任何响应都不回显。
 */
import { del, get, post, put } from './http';
import type { AttDevice, AttLocation } from '../types/api';

/* ---------------------------- 考勤围栏 ---------------------------- */

/** 围栏列表（GET /att/locations，enabled 可选过滤） */
export function listLocations(enabled?: boolean): Promise<AttLocation[]> {
  return get<AttLocation[]>('/att/locations', enabled === undefined ? undefined : { enabled });
}

/** 新建围栏（POST /att/locations）：radiusM 缺省 200 米、enabled 缺省启用 */
export function createLocation(data: Record<string, unknown>): Promise<AttLocation> {
  return post<AttLocation>('/att/locations', data);
}

/** 更新围栏（PUT /att/locations/{id}） */
export function updateLocation(id: number, data: Record<string, unknown>): Promise<AttLocation> {
  return put<AttLocation>(`/att/locations/${id}`, data);
}

/** 删除围栏（DELETE /att/locations/{id}） */
export function deleteLocation(id: number): Promise<void> {
  return del<void>(`/att/locations/${id}`);
}

/* ---------------------------- 考勤机设备 ---------------------------- */

/** 设备列表（GET /att/devices，status 可选过滤） */
export function listDevices(status?: string): Promise<AttDevice[]> {
  return get<AttDevice[]>('/att/devices', status ? { status } : undefined);
}

/** 新增设备（POST /att/devices）：deviceKey 创建时必填，留空表示不设置密钥 */
export function createDevice(data: Record<string, unknown>): Promise<AttDevice> {
  return post<AttDevice>('/att/devices', data);
}

/** 更新设备（PUT /att/devices/{id}）：deviceKey 留空表示不轮换密钥 */
export function updateDevice(id: number, data: Record<string, unknown>): Promise<AttDevice> {
  return put<AttDevice>(`/att/devices/${id}`, data);
}

/** 删除设备（DELETE /att/devices/{id}） */
export function deleteDevice(id: number): Promise<void> {
  return del<void>(`/att/devices/${id}`);
}
