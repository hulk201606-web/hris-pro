import type { Component } from 'vue';
import {
  BookOutlined,
  CalendarOutlined,
  DollarOutlined,
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
      { resource: 'candidate', route: '/admin/recruit/candidates', title: '候选人' },
      { resource: 'offer', route: '/admin/recruit/offers', title: 'Offer 管理' },
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
    ],
  },
  {
    code: 'kb',
    title: '知识库',
    icon: BookOutlined,
    resources: [
      { resource: 'space', route: '/admin/knowledge/spaces', title: '知识空间' },
      { resource: 'document', route: '/admin/knowledge/documents', title: '文档管理' },
      { resource: 'doc', route: '/admin/knowledge/scope', title: '知识范围' },
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
