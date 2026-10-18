<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { Component } from 'vue';
import {
  AuditOutlined,
  CalendarOutlined,
  DollarOutlined,
  FormOutlined,
  IdcardOutlined,
} from '@ant-design/icons-vue';
import { fetchTodoTasks } from '../../api/flow';
import { useUserStore } from '../../stores/user';

interface QuickEntry {
  title: string;
  desc: string;
  route: string;
  icon: Component;
}

const quickEntries: QuickEntry[] = [
  { title: '我的申请', desc: '请假 / 加班 / 出差 / 补卡', route: '/self/applications', icon: FormOutlined },
  { title: '我的审批', desc: '待办事项处理', route: '/self/approvals', icon: AuditOutlined },
  { title: '我的信息', desc: '查看个人档案', route: '/self/profile', icon: IdcardOutlined },
  { title: '我的薪酬', desc: '工资条查询', route: '/self/payroll', icon: DollarOutlined },
  { title: '我的假期', desc: '假期额度与流水', route: '/self/leave', icon: CalendarOutlined },
];

const router = useRouter();
const userStore = useUserStore();

const todoCount = ref(0);
// 假期额度占位：二期对接 GET /att/my/quotas（假勤域尚未纳入本期 openapi）
const leaveBalance = ref(12.5);
const loading = ref(true);

const displayName = computed(() => userStore.displayName || '同事');
const tenantName = computed(() => userStore.tenantName || '');

function go(route: string) {
  router.push(route);
}

onMounted(async () => {
  try {
    const page = await fetchTodoTasks(1, 1);
    todoCount.value = page.total;
  } catch {
    todoCount.value = 0;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="self-home">
    <section class="welcome">
      <h2 class="welcome-title">你好，{{ displayName }}</h2>
      <p class="welcome-sub">欢迎回到员工自助服务中心{{ tenantName ? ` · ${tenantName}` : '' }}</p>
    </section>

    <a-row :gutter="16" class="stats">
      <a-col :xs="24" :sm="12">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="待办事项" :value="todoCount" :loading="loading" suffix="件" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="剩余年假" :value="leaveBalance" precision="1" suffix="天" />
        </a-card>
      </a-col>
    </a-row>

    <section class="quick">
      <h3 class="section-title">快捷入口</h3>
      <a-row :gutter="[16, 16]">
        <a-col v-for="entry in quickEntries" :key="entry.route" :xs="12" :sm="8" :lg="6">
          <a-card hoverable :bordered="false" class="quick-card" @click="go(entry.route)">
            <div class="quick-body">
              <component :is="entry.icon" class="quick-icon" />
              <div>
                <div class="quick-title">{{ entry.title }}</div>
                <div class="quick-desc">{{ entry.desc }}</div>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </section>
  </div>
</template>

<style scoped>
.self-home {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.welcome-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.welcome-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.stats .stat-card,
.quick-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.section-title {
  margin: 0 0 var(--space-4);
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.quick-body {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.quick-icon {
  font-size: 22px;
  color: var(--color-text-secondary);
}

.quick-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.quick-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
