<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  DownOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons-vue';
import { useUserStore } from '../stores/user';
import { defaultOpenKeys, deriveAdminMenu } from '../router/admin-menu';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const collapsed = ref(false);
const openKeys = ref<string[]>(defaultOpenKeys());
const selectedKeys = computed(() => [route.path]);
const displayName = computed(() => userStore.displayName || '未登录');
const tenantName = computed(() => userStore.tenantName || '智云 HR');

// 由后端菜单树（GET /auth/menus）派生管理端侧栏，未命中注册表或未授权则不展示
const adminMenu = computed(() => deriveAdminMenu(userStore.menus));

function onMenuClick({ key }: { key: string | number }) {
  router.push(String(key));
}

function onOpenChange(keys: string[]) {
  openKeys.value = keys as string[];
}

async function onUserMenu({ key }: { key: string | number }) {
  if (key === 'self') {
    router.push('/self/home');
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
      // 用户信息拉取失败仅降级展示
    }
  }
});
</script>

<template>
  <a-layout class="admin-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible :trigger="null" :width="224">
      <div class="logo">
        <span class="logo-mark">智云 HR</span>
        <span v-if="!collapsed" class="logo-sub">管理后台</span>
      </div>
      <a-menu
        theme="dark"
        mode="inline"
        :selected-keys="selectedKeys"
        :open-keys="openKeys"
        @click="onMenuClick"
        @open-change="onOpenChange"
      >
        <a-sub-menu v-for="mod in adminMenu" :key="mod.key">
          <template #icon><component :is="mod.icon" /></template>
          <template #title>{{ mod.title }}</template>
          <a-menu-item v-for="item in mod.items" :key="item.key">{{ item.title }}</a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="admin-header">
        <div class="header-left">
          <button class="header-trigger" type="button" aria-label="折叠菜单" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </button>
          <span class="header-tenant">{{ tenantName }}</span>
        </div>

        <div class="header-right">
          <a-dropdown placement="bottomRight">
            <span class="header-user">
              <span class="header-user-name">{{ displayName }}</span>
              <DownOutlined class="header-user-caret" />
            </span>
            <template #overlay>
              <a-menu @click="onUserMenu">
                <a-menu-item key="self">
                  <HomeOutlined />
                  返回自助端
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

      <a-layout-content class="admin-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.admin-layout {
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

.admin-header {
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

.admin-content {
  padding: var(--space-6);
}
</style>
