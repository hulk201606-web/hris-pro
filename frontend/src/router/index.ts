/**
 * 路由表与全局守卫。
 * - /login 公开；/self/* 自助端；/admin/* 管理端；其余 404。
 * - 守卫：未登录跳登录页（带 redirect）；已登录访问登录页回自助首页。
 * - afterEach 写入文档标题「页面名 · 智云 HR」。
 */
import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useUserStore } from '../stores/user';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/self/home',
    children: [
      {
        path: 'self/home',
        name: 'SelfHome',
        component: () => import('../views/self/SelfHomeView.vue'),
        meta: { title: '自助首页' },
      },
      {
        path: 'self/profile',
        name: 'MyProfile',
        component: () => import('../views/self/MyProfileView.vue'),
        meta: { title: '我的信息' },
      },
      {
        path: 'self/applications',
        name: 'MyApplications',
        component: () => import('../views/self/MyApplicationsView.vue'),
        meta: { title: '我的申请' },
      },
      {
        path: 'self/approvals',
        name: 'MyApprovals',
        component: () => import('../views/self/MyApprovalsView.vue'),
        meta: { title: '我的审批' },
      },
      {
        path: 'self/leave/apply',
        name: 'LeaveApply',
        component: () => import('../views/self/LeaveApplyView.vue'),
        meta: { title: '请假申请' },
      },
      {
        path: 'self/overtime/apply',
        name: 'OvertimeApply',
        component: () => import('../views/self/OvertimeApplyView.vue'),
        meta: { title: '加班申请' },
      },
      {
        path: 'self/fix/apply',
        name: 'FixApply',
        component: () => import('../views/self/FixApplyView.vue'),
        meta: { title: '补卡申请' },
      },
      {
        path: 'self/payroll',
        name: 'MyPayroll',
        component: () => import('../views/self/PlaceholderView.vue'),
        meta: { title: '我的薪酬' },
        props: { title: '我的薪酬' },
      },
      {
        path: 'self/leave',
        name: 'MyLeave',
        component: () => import('../views/self/PlaceholderView.vue'),
        meta: { title: '我的假期' },
        props: { title: '我的假期' },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/admin/recruit/requisitions',
    children: [
      {
        path: 'recruit/requisitions',
        name: 'AdminRequisitions',
        component: () => import('../views/admin/recruit/RequisitionListView.vue'),
        meta: { title: '职位需求' },
      },
      {
        path: 'recruit/candidates',
        name: 'AdminCandidates',
        component: () => import('../views/admin/recruit/CandidateListView.vue'),
        meta: { title: '候选人' },
      },
      {
        path: 'recruit/offers',
        name: 'AdminOffers',
        component: () => import('../views/admin/recruit/OfferListView.vue'),
        meta: { title: 'Offer 管理' },
      },
      {
        path: 'attendance/groups',
        name: 'AdminAttGroups',
        component: () => import('../views/admin/attendance/GroupListView.vue'),
        meta: { title: '考勤组' },
      },
      {
        path: 'attendance/shifts',
        name: 'AdminAttShifts',
        component: () => import('../views/admin/attendance/ShiftListView.vue'),
        meta: { title: '班次' },
      },
      {
        path: 'attendance/leave-types',
        name: 'AdminAttLeaveTypes',
        component: () => import('../views/admin/attendance/LeaveTypeListView.vue'),
        meta: { title: '假期类型' },
      },
      {
        path: 'attendance/payroll-cycles',
        name: 'AdminAttCycles',
        component: () => import('../views/admin/attendance/PayrollCycleListView.vue'),
        meta: { title: '薪酬取数周期' },
      },
      {
        path: 'attendance/daily',
        name: 'AdminAttDaily',
        component: () => import('../views/admin/attendance/DailyAttendanceView.vue'),
        meta: { title: '日考勤' },
      },
      {
        path: 'attendance/exceptions',
        name: 'AdminAttExceptions',
        component: () => import('../views/admin/attendance/ExceptionWorkbenchView.vue'),
        meta: { title: '异常工作台' },
      },
      {
        path: 'compensation/payroll-runs',
        name: 'AdminPayrollRuns',
        component: () => import('../views/admin/compensation/PayrollRunListView.vue'),
        meta: { title: '薪酬核算' },
      },
      {
        path: 'compensation/preview',
        name: 'AdminPayrollPreview',
        component: () => import('../views/admin/compensation/PayrollPreviewView.vue'),
        meta: { title: '算薪预览' },
      },
      {
        path: 'compensation/employee-pays',
        name: 'AdminEmployeePays',
        component: () => import('../views/admin/compensation/EmployeePayListView.vue'),
        meta: { title: '员工薪酬' },
      },
      {
        path: 'performance/cycles',
        name: 'AdminCycles',
        component: () => import('../views/admin/performance/CycleListView.vue'),
        meta: { title: '考核周期' },
      },
      {
        path: 'performance/goals',
        name: 'AdminGoals',
        component: () => import('../views/admin/performance/GoalListView.vue'),
        meta: { title: '目标管理' },
      },
      {
        path: 'performance/context',
        name: 'AdminReviewContext',
        component: () => import('../views/admin/performance/ReviewContextView.vue'),
        meta: { title: '评估上下文' },
      },
      {
        path: 'knowledge/spaces',
        name: 'AdminSpaces',
        component: () => import('../views/admin/knowledge/SpaceListView.vue'),
        meta: { title: '知识空间' },
      },
      {
        path: 'knowledge/documents',
        name: 'AdminDocuments',
        component: () => import('../views/admin/knowledge/DocumentListView.vue'),
        meta: { title: '文档管理' },
      },
      {
        path: 'knowledge/scope',
        name: 'AdminKnowledgeScope',
        component: () => import('../views/admin/knowledge/KnowledgeScopeView.vue'),
        meta: { title: '知识范围' },
      },
      {
        path: 'org/legal-entities',
        name: 'AdminLegalEntities',
        component: () => import('../views/admin/org/LegalEntityListView.vue'),
        meta: { title: '法人主体' },
      },
      {
        path: 'org/depts',
        name: 'AdminDepts',
        component: () => import('../views/admin/org/DeptListView.vue'),
        meta: { title: '部门管理' },
      },
      {
        path: 'org/positions',
        name: 'AdminPositions',
        component: () => import('../views/admin/org/PositionListView.vue'),
        meta: { title: '岗位管理' },
      },
      {
        path: 'org/jobs',
        name: 'AdminJobs',
        component: () => import('../views/admin/org/JobListView.vue'),
        meta: { title: '职位管理' },
      },
      {
        path: 'org/grades',
        name: 'AdminGrades',
        component: () => import('../views/admin/org/GradeListView.vue'),
        meta: { title: '职级体系' },
      },
      {
        path: 'org/bands',
        name: 'AdminGradeBands',
        component: () => import('../views/admin/org/GradeBandListView.vue'),
        meta: { title: '职级带' },
      },
      {
        path: 'org/headcounts',
        name: 'AdminHeadcounts',
        component: () => import('../views/admin/org/HeadcountListView.vue'),
        meta: { title: '编制管理' },
      },
      {
        path: 'employee/roster',
        name: 'AdminRoster',
        component: () => import('../views/admin/employee/RosterListView.vue'),
        meta: { title: '员工花名册' },
      },
      {
        path: 'employee/contracts-expiring',
        name: 'AdminContractExpiring',
        component: () => import('../views/admin/employee/ContractExpiringView.vue'),
        meta: { title: '合同到期预警' },
      },
      {
        path: 'employee/lifecycle',
        name: 'AdminLifecycle',
        component: () => import('../views/admin/employee/LifecycleApplyView.vue'),
        meta: { title: '员工异动办理' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫：未登录跳登录页；已登录访问登录页则回首页
router.beforeEach((to) => {
  const userStore = useUserStore();
  const isPublic = to.meta.public === true;

  if (isPublic) {
    if (to.name === 'Login' && userStore.isAuthenticated) {
      return { path: '/self/home' };
    }
    return true;
  }

  if (!userStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '';
  document.title = title ? `${title} · 智云 HR` : '智云 HR';
});

export default router;
