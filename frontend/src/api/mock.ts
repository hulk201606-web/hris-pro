/**
 * 前端静态 Mock（后端未就绪时的降级数据源）。
 * - 由 VITE_USE_MOCK 控制：true 时 API 层返回本文件数据，false 时走真实网关。
 * - 数据均为可读的业务样例，不依赖真实后端即可联调页面。
 */
import type {
  EmployeeProfileResponse,
  LoginRequest,
  MenuNode,
  MeResponse,
  PageResult,
  StartInstanceRequest,
  StartInstanceResponse,
  TaskView,
  TokenResponse,
} from '../types/api';

export function isMockEnabled(): boolean {
  return import.meta.env.VITE_USE_MOCK === 'true';
}

function delay<T>(data: T, ms = 260): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(data), ms);
  });
}

/** 模拟登录：MVP 简化版仅做非空校验，任意合法账号均可进入（真实校验在后端） */
function mockLogin(req: LoginRequest): Promise<TokenResponse> {
  if (!req.tenantCode || !req.username || !req.password) {
    return Promise.reject(new Error('租户编码、账号、密码均不能为空'));
  }
  return delay<TokenResponse>({
    accessToken: 'mock-access-token.v1',
    refreshToken: 'mock-refresh-token.v1',
    tokenType: 'Bearer',
    expiresIn: 3600,
    refreshExpiresIn: 604800,
  });
}

function mockMe(): Promise<MeResponse> {
  return delay<MeResponse>({
    user: { userId: 1001, employeeId: 10001, username: 'wang.fang', status: 1 },
    roles: [{ code: 'EMPLOYEE_SELF', name: '员工自助' }],
    perms: [
      'self:home:view',
      'self:profile:view',
      'self:profile:edit',
      'self:application:create',
      'self:approval:view',
      'self:payroll:view',
      'self:leave:view',
      // 二期管理端权限点（Mock 管理员角色，便于演示页面按钮）
      'rcu:requisition:read',
      'rcu:requisition:write',
      'rcu:candidate:read',
      'rcu:candidate:write',
      'rcu:offer:read',
      'rcu:offer:write',
      'rcu:offer:onboard',
      'cmp:payroll-run:read',
      'cmp:payroll-run:write',
      'cmp:employee-pay:read',
      'cmp:employee-pay:write',
      'cmp:payroll:preview',
      'prf:cycle:read',
      'prf:cycle:write',
      'prf:goal:read',
      'prf:goal:write',
      'prf:review:read',
      'kb:space:read',
      'kb:space:write',
      'kb:document:read',
      'kb:document:write',
      'kb:doc:read',
      'att:group:write',
      'att:shift:write',
      'att:leave:type:write',
      'att:cycle:write',
      'att:daily:compute',
      'att:exception:handle',
    ],
    dataScopes: [{ roleCode: 'EMPLOYEE_SELF', scopeType: 'SELF', deptIds: '' }],
    tenant: {
      tenantId: 1,
      code: 'zhiyun',
      name: '智云科技有限公司',
      plan: 'PRO',
      timezone: 'Asia/Shanghai',
      domain: 'zhiyun.hris.local',
    },
  });
}

function mockMenus(): Promise<MenuNode[]> {
  return delay<MenuNode[]>([
    { code: 'self', name: '自助服务', type: 'module', children: [
      { code: 'self:home', name: '自助首页', type: 'menu', children: [] },
      { code: 'self:profile', name: '我的信息', type: 'menu', children: [] },
      { code: 'self:application', name: '我的申请', type: 'menu', children: [] },
      { code: 'self:approval', name: '我的审批', type: 'menu', children: [] },
      { code: 'self:payroll', name: '我的薪酬', type: 'menu', children: [] },
      { code: 'self:leave', name: '我的假期', type: 'menu', children: [] },
    ] },
    { code: 'rcu', name: '招聘管理', type: 'module', children: [
      { code: 'rcu:requisition', name: 'requisition', type: 'menu', children: [] },
      { code: 'rcu:candidate', name: 'candidate', type: 'menu', children: [] },
      { code: 'rcu:offer', name: 'offer', type: 'menu', children: [] },
    ] },
    { code: 'att', name: '假勤管理', type: 'module', children: [
      { code: 'att:group', name: 'group', type: 'menu', children: [] },
      { code: 'att:shift', name: 'shift', type: 'menu', children: [] },
      { code: 'att:leave', name: 'leave', type: 'menu', children: [] },
      { code: 'att:cycle', name: 'cycle', type: 'menu', children: [] },
      { code: 'att:daily', name: 'daily', type: 'menu', children: [] },
      { code: 'att:exception', name: 'exception', type: 'menu', children: [] },
    ] },
    { code: 'cmp', name: '薪酬管理', type: 'module', children: [
      { code: 'cmp:payroll-run', name: 'payroll-run', type: 'menu', children: [] },
      { code: 'cmp:payroll', name: 'payroll', type: 'menu', children: [] },
      { code: 'cmp:employee-pay', name: 'employee-pay', type: 'menu', children: [] },
    ] },
    { code: 'prf', name: '绩效培训', type: 'module', children: [
      { code: 'prf:cycle', name: 'cycle', type: 'menu', children: [] },
      { code: 'prf:goal', name: 'goal', type: 'menu', children: [] },
      { code: 'prf:review', name: 'review', type: 'menu', children: [] },
    ] },
    { code: 'kb', name: '知识库', type: 'module', children: [
      { code: 'kb:space', name: 'space', type: 'menu', children: [] },
      { code: 'kb:document', name: 'document', type: 'menu', children: [] },
      { code: 'kb:doc', name: 'doc', type: 'menu', children: [] },
    ] },
  ]);
}

function mockMyProfile(): Promise<EmployeeProfileResponse> {
  return delay<EmployeeProfileResponse>({
    id: 10001,
    empNo: 'ZY10021',
    name: '王芳',
    gender: 'F',
    birthDate: '1992-05-18',
    email: 'wang.fang@zhiyun.com',
    status: 2,
    statusLabel: '在职',
    blacklisted: 0,
    certNoMask: '1101**********0421',
    mobileMask: '138****1024',
    employment: {
      legalEntityId: 1,
      deptId: 101,
      positionId: 301,
      gradeId: 5,
      hireType: 'FULL_TIME',
      supervisorEmpId: 10008,
      effectiveFrom: '2021-07-01',
    },
    educations: [
      { id: 1, school: '华中科技大学', major: '人力资源管理', degree: '本科', startDate: '2010-09-01', endDate: '2014-06-30' },
    ],
    works: [
      { id: 1, company: '启明信息科技有限公司', positionName: '招聘专员', startDate: '2014-07-01', endDate: '2018-02-28', description: '负责社招与校招渠道运营' },
      { id: 2, company: '云帆软件股份有限公司', positionName: 'HRBP', startDate: '2018-03-01', endDate: '2021-06-30', description: '对接研发与产品团队' },
    ],
    families: [{ id: 1, name: '张伟', relation: '配偶', phone: '139****7721', company: '独立执业' }],
    emergencies: [{ id: 1, name: '王建国', relation: '父亲', phone: '136****3318', address: '湖北省武汉市' }],
  });
}

function mockTodoTasks(page: number, size: number): Promise<PageResult<TaskView>> {
  const all: TaskView[] = [
    {
      taskId: 't-8001',
      instanceId: 'pi-5001',
      procDefKey: 'LEAVE_APPLY',
      bizType: 'LEAVE_APPLY',
      bizKey: 'LA-2026-0041',
      nodeName: '部门负责人审批',
      assignee: '王芳',
      status: 'TODO',
      startTime: '2026-10-14T09:12:00Z',
      createTime: '2026-10-14T09:10:00Z',
      endTime: '',
      durationInMillis: 0,
    },
    {
      taskId: 't-8002',
      instanceId: 'pi-5002',
      procDefKey: 'OVERTIME_APPLY',
      bizType: 'OVERTIME_APPLY',
      bizKey: 'OT-2026-0028',
      nodeName: '人事复核',
      assignee: '王芳',
      status: 'TODO',
      startTime: '2026-10-13T15:40:00Z',
      createTime: '2026-10-13T15:38:00Z',
      endTime: '',
      durationInMillis: 0,
    },
    {
      taskId: 't-8003',
      instanceId: 'pi-5003',
      procDefKey: 'PROOF_APPLY',
      bizType: 'PROOF_APPLY',
      bizKey: 'PF-2026-0012',
      nodeName: 'HR 审核',
      assignee: '王芳',
      status: 'TODO',
      startTime: '2026-10-12T10:05:00Z',
      createTime: '2026-10-12T10:02:00Z',
      endTime: '',
      durationInMillis: 0,
    },
  ];
  const records = all.slice((page - 1) * size, page * size);
  return delay<PageResult<TaskView>>({ records, page, size, total: all.length });
}

function mockStartedTasks(page: number, size: number): Promise<PageResult<TaskView>> {
  return delay<PageResult<TaskView>>({ records: [], page, size, total: 0 });
}

function mockStartInstance(req: StartInstanceRequest): Promise<StartInstanceResponse> {
  return delay<StartInstanceResponse>({
    instanceId: `pi-mock-${Date.now()}`,
    bizType: req.bizType,
    bizKey: req.bizKey,
    status: 'RUNNING',
  });
}

export const mockApi = {
  login: mockLogin,
  me: mockMe,
  menus: mockMenus,
  myProfile: mockMyProfile,
  todoTasks: mockTodoTasks,
  startedTasks: mockStartedTasks,
  startInstance: mockStartInstance,
};
