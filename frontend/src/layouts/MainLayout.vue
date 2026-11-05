<script setup lang="ts">

/**
 * 自助端布局：顶部导航 + 左侧自助菜单 + 内容区。
 * 面向员工自助服务（请假/加班/补卡/我的审批/我的薪酬），
 * 路由前缀 /self。菜单当前为静态配置，后续由后端菜单树驱动。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { Component } from 'vue';
import {
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  DownOutlined,
  FormOutlined,
  HomeOutlined,
  IdcardOutlined,
  MessageOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined,
} from '@ant-design/icons-vue';
import { useUserStore } from '../stores/user';

interface MenuItem {
  key: string;
  label: string;
  icon: Component;
}

// 自助门户菜单（静态；后续由 GET /auth/menus 菜单树 + 本地 code→route/icon 注册表驱动）
const menuItems: MenuItem[] = [
  { key: '/self/home', label: '自助首页', icon: HomeOutlined },
  { key: '/self/chat', label: '智能问答', icon: RobotOutlined },
  { key: '/self/profile', label: '我的信息', icon: IdcardOutlined },
  { key: '/self/applications', label: '我的申请', icon: FormOutlined },
  { key: '/self/approvals', label: '我的审批', icon: AuditOutlined },
  { key: '/self/payroll', label: '我的薪酬', icon: DollarOutlined },
  { key: '/self/leave', label: '我的假期', icon: CalendarOutlined },
  { key: '/self/attendance', label: '我的考勤', icon: ClockCircleOutlined },
  { key: '/self/interviews', label: '我的面谈', icon: MessageOutlined },
];

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const collapsed = ref(false);
const selectedKeys = computed(() => [route.path]);
const displayName = computed(() => userStore.displayName || '未登录');
const tenantName = computed(() => userStore.tenantName || '智云 HR');

function onMenuClick({ key }: { key: string | number }) {
  router.push(String(key));
}

async function onUserMenu({ key }: { key: string | number }) {
  if (key === 'admin') {
    router.push('/admin/recruit/requisitions');
    return;
  }
  if (key === 'logout') {
    await userStore.logout();
    router.push('/login');
  }
}

onMounted(async () => {
  if (!userStore.loaded) {
    try {
      await userStore.loadProfile();
      await userStore.loadMenus();
    } catch {
      // 用户信息拉取失败仅降级展示，各数据页面自行兜底
    }
  }
});
</script>

<template>
  <a-layout class="app-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible :trigger="null" :width="224">
      <div class="logo">
        <span class="logo-mark">智云 HR</span>
        <span v-if="!collapsed" class="logo-sub">自助服务中心</span>
      </div>
      <a-menu theme="dark" mode="inline" :selected-keys="selectedKeys" @click="onMenuClick">
        <a-menu-item v-for="item in menuItems" :key="item.key">
          <template #icon>
            <component :is="item.icon" />
          </template>
          {{ item.label }}
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="app-header">
        <div class="header-left">
          <button class="header-trigger" type="button" aria-label="折叠菜单" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </button>
          <span class="header-tenant">{{ tenantName }}</span>
        </div>

        <div class="header-right">
          <a-badge dot>
            <button class="header-trigger" type="button" aria-label="通知中心">
              <BellOutlined />
            </button>
          </a-badge>

          <a-dropdown placement="bottomRight">
            <span class="header-user">
              <span class="header-user-name">{{ displayName }}</span>
              <DownOutlined class="header-user-caret" />
            </span>
            <template #overlay>
              <a-menu @click="onUserMenu">
                <a-menu-item key="admin">
                  <AppstoreOutlined />
                  管理后台
                </a-menu-item>
                <a-menu-item key="logout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.logo {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 56px;
  padding: 0 var(--space-4);
  overflow: hidden;
}

.logo-mark {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-white);
  letter-spacing: var(--ls-caps);
  white-space: nowrap;
}

.logo-sub {
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 var(--space-4);
  line-height: 56px;
  background: var(--color-bg-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.header-tenant {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.header-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 16px;
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.header-trigger:hover {
  background: var(--color-bg-page);
  color: var(--color-text-primary);
}

.header-user {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  color: var(--color-text-primary);
}

.header-user-name {
  font-size: 14px;
}

.header-user-caret {
  font-size: 12px;
  color: var(--color-text-muted);
}

.app-content {
  padding: var(--space-6);
}
</style>
