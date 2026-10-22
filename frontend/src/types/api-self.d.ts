/**
 * 前后端共享接口类型契约 —— 自助聚合模块（本阶段占位）
 */

/** 自助首页摘要（SELF-F-001）：待办数 + 假期余额 */
export interface SelfHomeSummary {
  /** 我的待办数量 */
  todoCount: number;
  /** 剩余年假天数（假期额度占位，二期对接 att/quotas） */
  leaveBalanceDays: number;
  /** 在职状态文案 */
  statusLabel: string;
}
