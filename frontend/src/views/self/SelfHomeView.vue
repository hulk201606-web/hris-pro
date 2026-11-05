<script setup lang="ts">

/**
 * 自助首页（SELF-F-001）：单端点聚合 GET /emp/me/dashboard。
 * 后端并行拉取资料/考勤/假期/工资条/待办并独立 fail-open，
 * 子项 source=fallback 或 partial=true 时，对应卡片展示降级提示（不再前端串行拼装）。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { Component } from 'vue';
import {
  AuditOutlined,
  BellOutlined,
  CalendarOutlined,
  CarOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  ExportOutlined,
  FormOutlined,
  IdcardOutlined,
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { fetchSelfDashboard } from '../../api/self';
import { BizError } from '../../api/error';
import { useUserStore } from '../../stores/user';
import type { SelfDashboard } from '../../types/api';

interface QuickEntry {
  title: string;
  desc: string;
  route: string;
  icon: Component;
}

const quickEntries: QuickEntry[] = [
  { title: '我的申请', desc: '请假 / 加班 / 出差 / 补卡', route: '/self/applications', icon: FormOutlined },
  { title: '我的审批', desc: '待办事项处理', route: '/self/approvals', icon: AuditOutlined },
  { title: '我的考勤', desc: '今日打卡与假勤台账', route: '/self/attendance', icon: ClockCircleOutlined },
  { title: '出差申请', desc: '差旅行程发起', route: '/self/trip/apply', icon: CarOutlined },
  { title: '外出/居家', desc: '外出与居家办公申请', route: '/self/out-office/apply', icon: ExportOutlined },
  { title: '通知偏好', desc: '通知渠道与免打扰', route: '/self/notify-preferences', icon: BellOutlined },
  { title: '我的信息', desc: '个人档案与变更申请', route: '/self/profile', icon: IdcardOutlined },
  { title: '我的薪酬', desc: '工资条查询', route: '/self/payroll', icon: DollarOutlined },
  { title: '我的假期', desc: '假期额度与流水', route: '/self/leave', icon: CalendarOutlined },
];

/** 日考勤状态码文案（att_daily.att_status） */
const attStatusLabels: Record<string, string> = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY: '早退',
  ABSENT: '缺勤',
  LEAVE: '请假',
  NO_RECORD: '暂无记录',
};

const router = useRouter();
const userStore = useUserStore();

const dash = ref<SelfDashboard | null>(null);
const loading = ref(true);

const displayName = computed(() => userStore.displayName || '同事');
const tenantName = computed(() => userStore.tenantName || '');

/** 子项是否降级（source=fallback 由后端标记） */
const fallback = computed(() => ({
  profile: dash.value?.profile?.source === 'fallback',
  attendance: dash.value?.attendance?.source === 'fallback',
  leaves: dash.value?.leaves?.source === 'fallback',
  payslip: dash.value?.payslip?.source === 'fallback',
  flows: dash.value?.pendingFlows?.source === 'fallback',
}));

const todoCount = computed(() => dash.value?.pendingFlows?.count ?? 0);
const leaveDays = computed(() =>
  (dash.value?.leaves?.items ?? []).reduce((sum, i) => sum + Number(i.remainingDays || 0), 0),
);
const attStatus = computed(() => {
  const code = dash.value?.attendance?.todayStatus ?? 'NO_RECORD';
  return attStatusLabels[code] ?? code;
});
const lateCount = computed(() => dash.value?.attendance?.monthLateCount ?? 0);
const payslipText = computed(() => {
  const p = dash.value?.payslip;
  if (!p?.latestPeriod) return '暂无工资条';
  return `${p.latestPeriod} · ${p.netAmountMasked || '金额待发布'}`;
});

function go(route: string) {
  router.push(route);
}

onMounted(async () => {
  try {
    dash.value = await fetchSelfDashboard();
  } catch (err) {
    message.error(err instanceof BizError ? err.message : '首页数据加载失败');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="self-home">
    <section class="welcome">
      <h2 class="welcome-title">你好，{{ displayName }}</h2>
      <p class="welcome-sub">
        欢迎回到员工自助服务中心{{ tenantName ? ` · ${tenantName}` : '' }}
        <span v-if="dash" class="cost">数据耗时 {{ dash.costMs }} ms</span>
      </p>
    </section>

    <a-alert
      v-if="dash?.partial"
      type="warning"
      show-icon
      message="部分数据暂时不可用"
      description="下方标注「降级」的卡片未能取到下游数据，其余内容不受影响，可稍后刷新重试。"
    />

    <a-row :gutter="16">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="待办事项" :value="todoCount" :loading="loading" suffix="件" />
          <p v-if="fallback.flows" class="degraded">降级：待办服务不可用</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="剩余假期" :value="leaveDays" :loading="loading" :precision="1" suffix="天" />
          <p v-if="fallback.leaves" class="degraded">降级：假期服务不可用</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="今日考勤" :value="attStatus" :loading="loading" />
          <p class="sub">本月迟到 {{ lateCount }} 次</p>
          <p v-if="fallback.attendance" class="degraded">降级：考勤服务不可用</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic title="最新工资条" :value="payslipText" :loading="loading" />
          <p v-if="fallback.payslip" class="degraded">降级：薪酬服务不可用</p>
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" class="profile-card" title="我的档案">
      <a-descriptions v-if="dash" :column="{ xs: 1, sm: 2, md: 4 }" size="small">
        <a-descriptions-item label="姓名">{{ dash.profile.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="工号">{{ dash.profile.empNo || '—' }}</a-descriptions-item>
        <a-descriptions-item label="部门">{{ dash.profile.deptName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="岗位">{{ dash.profile.positionName || '—' }}</a-descriptions-item>
      </a-descriptions>
      <p v-if="fallback.profile" class="degraded">降级：组织信息暂不可用</p>
    </a-card>

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
  gap: var(--space-5);
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

.cost {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--color-text-muted);
}

.stat-card,
.profile-card,
.quick-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.sub {
  margin: var(--space-2) 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.degraded {
  margin: var(--space-2) 0 0;
  font-size: 12px;
  color: var(--color-warning, #d48806);
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
