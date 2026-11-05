import type { Component } from 'vue';
import {
  ApartmentOutlined,
  BookOutlined,
  CalendarOutlined,
  DollarOutlined,
  IdcardOutlined,
  NodeIndexOutlined,
  SettingOutlined,
  TeamOutlined,
  TrophyOutlined,
} from '@ant-design/icons-vue';
import type { MenuNode } from '../types/api';

/**
 * 管理端菜单注册表：由后端权限点派生树（module → menu(资源) → button）映射到
 * 前端路由 / 图标 / 标题。侧栏只渲染注册表中命中的资源，未映射的资源静默跳过。
 *
 * 后端 MenuService.build 用 ":" 切分权限点，module=parts[0]、resource=parts[1]、
 * 其余为动作。因此注册表以「资源段（第二段）」为匹配键（如 employee-pay、payroll-run、
 * leave、doc、space），模块 code 为分组键。
 */

interface MenuMeta {
  /** 资源段（权限点第二段，如 "requisition" / "employee-pay" / "leave" / "doc"） */
  resource: string;
  /** 前端路由 */
  route: string;
  /** 菜单标题 */
  title: string;
}

interface ModuleMeta {
  code: string;
  title: string;
  icon: Component;
  resources: MenuMeta[];
}

const MODULES: ModuleMeta[] = [
  {
    code: 'rcu',
    title: '招聘管理',
    icon: TeamOutlined,
    resources: [
      { resource: 'requisition', route: '/admin/recruit/requisitions', title: '职位需求' },
      { resource: 'requisition', route: '/admin/recruit/funnel', title: '招聘漏斗' },
      { resource: 'candidate', route: '/admin/recruit/candidates', title: '候选人' },
      { resource: 'candidate', route: '/admin/recruit/candidate-dedup', title: '候选人查重' },
      { resource: 'application', route: '/admin/recruit/applications', title: '投递阶段' },
      { resource: 'resume', route: '/admin/recruit/resumes', title: '简历库' },
      { resource: 'offer', route: '/admin/recruit/offers', title: 'Offer 管理' },
      // 站点域名（批 7 #13）：权限点 rcu:site:read/write，资源段为 site
      { resource: 'site', route: '/admin/recruit/site-domains', title: '站点域名' },
    ],
  },
  {
    code: 'att',
    title: '考勤管理',
    icon: CalendarOutlined,
    resources: [
      { resource: 'group', route: '/admin/attendance/groups', title: '考勤组' },
      { resource: 'shift', route: '/admin/attendance/shifts', title: '班次' },
      { resource: 'leave', route: '/admin/attendance/leave-types', title: '假期类型' },
      { resource: 'cycle', route: '/admin/attendance/payroll-cycles', title: '薪酬取数周期' },
      { resource: 'daily', route: '/admin/attendance/daily', title: '日考勤' },
      { resource: 'exception', route: '/admin/attendance/exceptions', title: '异常工作台' },
      { resource: 'report', route: '/admin/attendance/reports', title: '考勤报表' },
      { resource: 'holiday', route: '/admin/attendance/holidays', title: '节假日历' },
      { resource: 'out-office', route: '/admin/attendance/out-offices', title: '外出/居家审批' },
      // 批 11/12 追加：围栏与考勤机后端复用 att:group:read/write；月结锁读也复用 group，
      // 写操作（月结/解锁）在前端按 att:closing:run 单独控显，故三项统一挂 group 资源键
      { resource: 'group', route: '/admin/attendance/locations', title: '考勤围栏' },
      { resource: 'group', route: '/admin/attendance/devices', title: '考勤机设备' },
      { resource: 'group', route: '/admin/attendance/daily-close', title: '月结锁管理' },
    ],
  },
  {
    code: 'cmp',
    title: '薪酬管理',
    icon: DollarOutlined,
    resources: [
      { resource: 'payroll-run', route: '/admin/compensation/payroll-runs', title: '薪酬核算' },
      { resource: 'payroll', route: '/admin/compensation/preview', title: '算薪预览' },
      { resource: 'employee-pay', route: '/admin/compensation/employee-pays', title: '员工薪酬' },
      // 期间关账权限复用 payroll-run 读写码（后端不设独立资源），故挂 payroll-run 资源键
      { resource: 'payroll-run', route: '/admin/compensation/period-locks', title: '期间关账' },
      // 薪资项 / 算薪监控 / 公式沙箱 / 法人成本报表：端点权限见各自 Controller（cmp:pay-item:*
      // 与 cmp:payroll-run:*），菜单显隐统一复用 payroll-run 资源键
      { resource: 'payroll-run', route: '/admin/compensation/pay-items', title: '薪资项管理' },
      { resource: 'payroll-run', route: '/admin/compensation/calc-monitor', title: '算薪监控' },
      { resource: 'payroll-run', route: '/admin/compensation/formula-sandbox', title: '公式沙箱' },
      { resource: 'payroll-run', route: '/admin/compensation/legal-entity-cost', title: '多法人成本报表' },
    ],
  },
  {
    code: 'prf',
    title: '绩效管理',
    icon: TrophyOutlined,
    resources: [
      { resource: 'cycle', route: '/admin/performance/cycles', title: '考核周期' },
      { resource: 'goal', route: '/admin/performance/goals', title: '目标管理' },
      { resource: 'review', route: '/admin/performance/context', title: '评估上下文' },
      { resource: 'evaluation', route: '/admin/performance/evaluations', title: '评分记录' },
      { resource: 'interview', route: '/admin/performance/interviews', title: '面谈记录' },
    ],
  },
  {
    code: 'kb',
    title: '知识库',
    icon: BookOutlined,
    resources: [
      { resource: 'space', route: '/admin/knowledge/spaces', title: '知识空间' },
      { resource: 'document', route: '/admin/knowledge/documents', title: '文档管理' },
      // 敏感词复用 kb:document:read/write 权限码（后端 SensitiveWordController）
      { resource: 'document', route: '/admin/knowledge/sensitive-words', title: '敏感词词库' },
      { resource: 'chunk', route: '/admin/knowledge/chunks', title: '知识切片' },
      { resource: 'doc', route: '/admin/knowledge/scope', title: '知识范围' },
    ],
  },
  {
    // 组织架构：一期模块，权限点形如 org:dept:read / org:grade-band:write。
    // 注意 grade-band 的第二段含连字符，与 grade 是两个不同资源，不能混。
    code: 'org',
    title: '组织架构',
    icon: ApartmentOutlined,
    resources: [
      { resource: 'legal-entity', route: '/admin/org/legal-entities', title: '法人主体' },
      { resource: 'dept', route: '/admin/org/depts', title: '部门管理' },
      { resource: 'position', route: '/admin/org/positions', title: '岗位管理' },
      { resource: 'job', route: '/admin/org/jobs', title: '职位管理' },
      { resource: 'grade', route: '/admin/org/grades', title: '职级体系' },
      { resource: 'grade-band', route: '/admin/org/bands', title: '职级带' },
      { resource: 'headcount', route: '/admin/org/headcounts', title: '编制管理' },
      { resource: 'headcount', route: '/admin/org/headcount-board', title: '编制看板' },
      // 人力结构分析（P1 聚合报表）：后端端点挂 org:dept:read，菜单挂 dept 资源键
      { resource: 'dept', route: '/admin/org/headcount-structure', title: '人力结构分析' },
      // 组织版本：后端 OrgVersionController 复用 org:dept:read/write
      { resource: 'dept', route: '/admin/org/versions', title: '组织版本' },
    ],
  },
  {
    // 员工管理：一期模块，权限点形如 emp:employee:read / emp:contract:write。
    // 四种异动（入职/转正/调动/离职）共用 emp:lifecycle:write，暂无独立页面。
    code: 'emp',
    title: '员工管理',
    icon: IdcardOutlined,
    resources: [
      { resource: 'employee', route: '/admin/employee/roster', title: '员工花名册' },
      { resource: 'employee', route: '/admin/employee/rehire-check', title: '再入职校验' },
      { resource: 'lifecycle', route: '/admin/employee/lifecycle', title: '员工异动办理' },
      { resource: 'contract', route: '/admin/employee/contracts-expiring', title: '合同到期预警' },
    ],
  },
  {
    // 流程中心：权限点形如 flow:model:write / flow:task:read。
    // 待办中心对四类视图（待办/已办/抄送/我发起）共用 flow:task 资源；
    // 委托代理按契约以 flow:task:write 控显，同样挂 task 资源。
    code: 'flow',
    title: '流程中心',
    icon: NodeIndexOutlined,
    resources: [
      { resource: 'model', route: '/admin/flow/models', title: '流程模型' },
      { resource: 'task', route: '/admin/flow/tasks', title: '待办中心' },
      { resource: 'task', route: '/admin/flow/delegates', title: '委托代理' },
    ],
  },
  {
    // 平台支撑：权限点形如 plt:dict:write / plt:report:export。
    // 字典类型与字典项共用一个 dict 资源，故两条菜单项会同时命中。
    code: 'plt',
    title: '平台支撑',
    icon: SettingOutlined,
    resources: [
      { resource: 'audit', route: '/admin/platform/audit-logs', title: '审计日志' },
      { resource: 'config', route: '/admin/platform/configs', title: '系统配置' },
      { resource: 'dict', route: '/admin/platform/dict-types', title: '字典类型' },
      { resource: 'dict', route: '/admin/platform/dict-items', title: '字典项' },
      { resource: 'notification', route: '/admin/platform/templates', title: '通知模板' },
      { resource: 'report', route: '/admin/platform/reports', title: '报表定义' },
      { resource: 'report', route: '/admin/platform/exports', title: '导出记录' },
      // 报表订阅（PLT-F-005）：plt:report:read/write
      { resource: 'report', route: '/admin/platform/report-subscriptions', title: '报表订阅' },
      { resource: 'scheduler', route: '/admin/platform/scheduler', title: '调度中心' },
    ],
  },
];

/** 供侧栏渲染的节点：模块（含命中的资源列表） */
export interface AdminMenuModule {
  key: string;
  title: string;
  icon: Component;
  items: { key: string; title: string }[];
}

/**
 * 由后端菜单树 + 本地注册表派生管理端侧栏结构。
 * 后端树形如：module(一级) → menu(资源, 二级) → button(动作)。
 * 命中规则：模块 code 匹配 + 后端资源的「第二段」命中注册表 resource 键。
 */
export function deriveAdminMenu(menus: MenuNode[]): AdminMenuModule[] {
  const result: AdminMenuModule[] = [];
  for (const moduleMeta of MODULES) {
    const moduleNode = menus.find((m) => m.code === moduleMeta.code);
    if (!moduleNode || !moduleNode.children?.length) continue;

    // 后端资源节点 code 形如 "rcu:requisition"；取第二段作资源名
    const resourceNames = new Set(
      moduleNode.children.map((c) => {
        const seg = c.code.split(':');
        return seg.length >= 2 ? seg[1] : c.code;
      }),
    );

    const items: { key: string; title: string }[] = [];
    for (const res of moduleMeta.resources) {
      if (resourceNames.has(res.resource)) {
        items.push({ key: res.route, title: res.title });
      }
    }
    if (!items.length) continue;

    result.push({
      key: moduleMeta.code,
      title: moduleMeta.title,
      icon: moduleMeta.icon,
      items,
    });
  }
  return result;
}

/** 默认打开的模块 key 列表 */
export function defaultOpenKeys(): string[] {
  return MODULES.map((m) => m.code);
}
