import { BizError } from '../../api/error';
import type { CrudColumn } from '../../types/admin';

/**
 * 单元格取值格式化（EntityCrudPage / SimpleCrudPage 共用，避免两侧口径漂移）。
 *
 * 规则优先级：空值占位 → valueMap 文案映射 → 日期/日期时间/时间截断 → 原值。
 * 注意：后端 LocalDateTime 序列化为无时区纳秒串（见 docs/15 §10.2），故按字符串截断而非 Date 解析。
 */
export function formatCell(col: CrudColumn | undefined, value: unknown): string {
  if (value === null || value === undefined || value === '') return '—';
  if (col?.valueMap && col.valueMap[String(value)] !== undefined) return col.valueMap[String(value)];
  if (col?.type === 'date' || col?.type === 'datetime') {
    const text = String(value).replace('T', ' ');
    return col.type === 'datetime' ? text.slice(0, 16) : text.slice(0, 10);
  }
  if (col?.type === 'time') return String(value).slice(0, 5);
  return String(value);
}

/** 业务状态到 Ant Design 预设标签色的映射（非 hex，符合颜色门禁） */
const TAG_COLORS: Record<string, string> = {
  APPROVED: 'green',
  ACCEPTED: 'green',
  ACTIVE: 'green',
  CLOSED: 'default',
  HIRED: 'green',
  RUNNING: 'blue',
  CALCULATING: 'blue',
  PENDING: 'gold',
  SCREENING: 'gold',
  INTERVIEWING: 'cyan',
  REJECTED: 'red',
  DRAFT: 'default',
  OPEN: 'gold',
  RESOLVED: 'green',
  IGNORED: 'default',
  NORMAL: 'green',
  LATE: 'orange',
  EARLY: 'orange',
  ABSENT: 'red',
  LEAVE: 'blue',
  MISSING_PUNCH: 'purple',
  '1': 'blue',
  '0': 'default',
};

export function resolveTagColor(value: unknown): string {
  return TAG_COLORS[String(value)] ?? 'default';
}

/** 统一错误文案：业务异常取后端 message，其余降级到兜底文案 */
export function errorText(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback;
}
