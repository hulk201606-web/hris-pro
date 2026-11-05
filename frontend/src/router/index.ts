/**
 * 路由表与全局守卫。
 * - /login 公开；/self/* 自助端；/admin/* 管理端；其余 404。
 * - 守卫：未登录跳登录页（带 redirect）；已登录访问登录页回自助首页。
 * - afterEach 写入文档标题「页面名 · 智云 HR」。
 */
import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useUserStore } from '../stores/user';
import { adminChildren } from './admin-routes';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    // 公开招聘段（RCS-F-004）：官网免登录职位列表，独立于 MainLayout
    path: '/public/jobs',
    name: 'PublicJobs',
    component: () => import('../views/public/PublicJobsView.vue'),
    meta: { public: true, title: '招聘职位' },
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
        path: 'self/chat',
        name: 'KbChat',
        component: () => import('../views/self/ChatView.vue'),
        meta: { title: '智能问答' },
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
        path: 'self/trip/apply',
        name: 'TripApply',
        component: () => import('../views/self/TripApplyView.vue'),
        meta: { title: '出差申请' },
      },
      {
        path: 'self/out-office/apply',
        name: 'OutOfficeApply',
        component: () => import('../views/self/OutOfficeApplyView.vue'),
        meta: { title: '外出/居家申请' },
      },
      {
        path: 'self/notify-preferences',
        name: 'NotifyPreferences',
        component: () => import('../views/self/NotifyPreferenceView.vue'),
        meta: { title: '通知偏好' },
      },
      {
        path: 'self/payroll',
        name: 'MyPayroll',
        component: () => import('../views/self/MyPayrollView.vue'),
        meta: { title: '我的薪酬' },
      },
      {
        path: 'self/leave',
        name: 'MyLeave',
        component: () => import('../views/self/MyLeaveView.vue'),
        meta: { title: '我的假期' },
      },
      {
        // 批 11#20：今日打卡卡片 + 假勤台账分页（GET /att/daily/today、/att/ledgers）
        path: 'self/attendance',
        name: 'MyAttendance',
        component: () => import('../views/self/MyAttendanceView.vue'),
        meta: { title: '我的考勤' },
      },
      {
        path: 'self/interviews',
        name: 'MyInterviews',
        component: () => import('../views/self/MyInterviewsView.vue'),
        meta: { title: '我的面谈' },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/admin/recruit/requisitions',
    children: adminChildren,
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
