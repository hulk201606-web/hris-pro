/**
 * 前后端共享接口类型契约 —— 工作流任务、审批发起单与考勤请求单
 */

/* ============================ flow 任务视图 ============================ */

export interface TaskView {
  taskId: string | null;
  instanceId: string;
  procDefKey: string;
  bizType: string;
  bizKey: string;
  nodeName: string;
  assignee: string;
  /** TODO / DONE / RUNNING / FINISHED */
  status: string;
  startTime: string;
  createTime: string;
  endTime: string;
  durationInMillis: number;
  /** 所属模型配置的动态表单 Schema（JSON 字符串），非空时随待办返回，前端可渲染 */
  formSchemaJson?: string | null;
  /** 是否已超期（配置了超时时长且任务超期） */
  overdue?: boolean;
  delegatedFrom?: number | null;  // 契约 FlowTaskView.delegatedFrom（委托代办人）
}

export interface TaskActionRequest {
  comment?: string;
  attachments?: number[];
  targetUserId?: number;
  /** 驳回语义开关（仅 reject 生效）：true=终止实例 */
  terminate?: boolean;
  /** 动态表单值（键=字段名），随审批动作落库 */
  formValues?: Record<string, unknown>;
  /** 结构化意见标签（approve/reject 可选）：RECHECK_DATA/NEED_MORE_INFO/COMPLIANT/OTHER */
  tag?: string;
}

/* ============================ 审批流（flow + 考勤请求单） ============================ */

/** 流程发起请求 */
export interface StartInstanceRequest {
  bizType: string;
  bizKey: string;
  procDefKey?: string;
  payload?: Record<string, unknown>;
  variables?: Record<string, unknown>;
  initiator?: number;
  assignee?: number;
  ccEmpIds?: number[];  // 契约 FlowStartInstanceRequest.ccEmpIds（抄送）
}

export interface StartInstanceResponse {
  instanceId: string;
  bizType: string;
  bizKey: string;
  status: string;
}

/** 请假单 */
export interface LeaveRequest {
  id: number;
  empId: number;
  leaveTypeId: number;
  leaveTypeName: string;
  bizKey: string;
  startTime: string;
  endTime: string;
  days: number;
  reason: string;
  status: string;
  procInstId: string;
  createdAt: string;
  updatedAt: string;
}

export interface LeaveCreateRequest {
  empId: number;
  leaveTypeId: number;
  startTime: string;
  endTime: string;
  reason?: string;
}

/** 加班单 */
export interface OvertimeRequest {
  id: number;
  empId: number;
  bizKey: string;
  startTime: string;
  endTime: string;
  hours: number;
  reason: string;
  mode: string;
  status: string;
  procInstId: string;
  dayType: string;
  rate: number;
  createdAt: string;
  updatedAt: string;
}

export interface OvertimeCreateRequest {
  empId: number;
  startTime: string;
  endTime: string;
  reason?: string;
  mode?: string;
}

/** 出差单 */
export interface TripRequest {
  id: number;
  empId: number;
  bizKey: string;
  startTime: string;
  endTime: string;
  destination: string;
  reason: string;
  status: string;
  procInstId: string;
  /** 补贴天数（按自然日跨度派生，供薪酬域计算） */
  subsidyDays: number;
  createdAt: string;
  updatedAt: string;
}

export interface TripCreateRequest {
  empId: number;
  startTime: string;
  endTime: string;
  destination: string;
  reason?: string;
}

/** 补卡单 */
export interface FixRequest {
  id: number;
  empId: number;
  bizKey: string;
  workDate: string;
  fixTime: string;
  type: string;
  reason: string;
  status: string;
  procInstId: string;
  createdAt: string;
  updatedAt: string;
}

export interface FixCreateRequest {
  empId: number;
  workDate: string;
  fixTime: string;
  type: string;
  reason?: string;
}

/** 考勤请求单查询参数 */
export interface AttRequestQuery {
  empId?: number;
  status?: string;
  from?: string;
  to?: string;
}
