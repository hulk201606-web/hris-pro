<script setup lang="ts">
import { useRouter } from 'vue-router';
import type { Component } from 'vue';
import {
  CalendarOutlined,
  CarOutlined,
  ClockCircleOutlined,
  FileProtectOutlined,
  ScheduleOutlined,
} from '@ant-design/icons-vue';

interface ApplicationEntry {
  key: string;
  title: string;
  desc: string;
  icon: Component;
  route: string;
  available: boolean;
}

// 我的申请（SELF-F-003）：聚合请假/加班/出差/补卡/证明发起入口。
// 请假/加班/补卡已接入真实发起页；出差/证明暂为占位。
const entries: ApplicationEntry[] = [
  { key: 'leave', title: '请假申请', desc: '年假 / 事假 / 病假', icon: CalendarOutlined, route: '/self/leave/apply', available: true },
  { key: 'overtime', title: '加班申请', desc: '工作日 / 休息日加班', icon: ClockCircleOutlined, route: '/self/overtime/apply', available: true },
  { key: 'fix', title: '补卡申请', desc: '漏打卡补录', icon: ScheduleOutlined, route: '/self/fix/apply', available: true },
  { key: 'trip', title: '出差申请', desc: '差旅行程与费用', icon: CarOutlined, route: '', available: false },
  { key: 'proof', title: '证明开具', desc: '在职 / 收入证明', icon: FileProtectOutlined, route: '', available: false },
];

const router = useRouter();

function go(entry: ApplicationEntry) {
  if (entry.available) router.push(entry.route);
}
</script>

<template>
  <div class="my-applications">
    <section class="head">
      <h2 class="head-title">我的申请</h2>
      <p class="head-sub">选择要发起的申请类型，进度将统一在此跟踪</p>
    </section>

    <a-row :gutter="[16, 16]">
      <a-col v-for="entry in entries" :key="entry.key" :xs="12" :sm="8" :lg="6">
        <a-card
          :bordered="false"
          :class="['entry-card', { available: entry.available }]"
          :hoverable="entry.available"
          @click="go(entry)"
        >
          <div class="entry-body">
            <component :is="entry.icon" class="entry-icon" />
            <div class="entry-title">{{ entry.title }}</div>
            <div class="entry-desc">{{ entry.desc }}</div>
          </div>
          <a-tag :class="['entry-tag']" :color="entry.available ? 'blue' : undefined">
            {{ entry.available ? '去发起' : '即将开放' }}
          </a-tag>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.my-applications {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.head-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.head-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.entry-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.entry-card.available {
  cursor: pointer;
}

.entry-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.entry-icon {
  font-size: 22px;
  color: var(--color-text-secondary);
}

.entry-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.entry-desc {
  font-size: 12px;
  color: var(--color-text-muted);
}

.entry-tag {
  margin-top: var(--space-3);
}
</style>
