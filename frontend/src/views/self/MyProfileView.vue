<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { fetchMyProfile } from '../../api/employee';
import { BizError } from '../../api/error';
import { useUserStore } from '../../stores/user';
import type { EmployeeProfileResponse } from '../../types/api';

const userStore = useUserStore();
const profile = ref<EmployeeProfileResponse | null>(null);
const loading = ref(true);

function genderLabel(g?: string): string {
  if (g === 'M') return '男';
  if (g === 'F') return '女';
  return g ?? '—';
}

onMounted(async () => {
  try {
    profile.value = await fetchMyProfile(userStore.employeeId);
  } catch (err) {
    const msg = err instanceof BizError ? err.message : '档案加载失败';
    message.error(msg);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="my-profile">
    <a-spin :spinning="loading">
      <template v-if="profile">
        <a-card :bordered="false" class="profile-card" title="基本信息">
          <template #extra>
            <span class="mask-hint">证件号与手机号已脱敏展示</span>
          </template>
          <a-descriptions :column="{ xs: 1, sm: 2, md: 3 }">
            <a-descriptions-item label="工号">{{ profile.empNo || '—' }}</a-descriptions-item>
            <a-descriptions-item label="姓名">{{ profile.name || '—' }}</a-descriptions-item>
            <a-descriptions-item label="性别">{{ genderLabel(profile.gender) }}</a-descriptions-item>
            <a-descriptions-item label="出生日期">{{ profile.birthDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="邮箱">{{ profile.email || '—' }}</a-descriptions-item>
            <a-descriptions-item label="在职状态">{{ profile.statusLabel || '—' }}</a-descriptions-item>
            <a-descriptions-item label="证件号">{{ profile.certNoMask || '—' }}</a-descriptions-item>
            <a-descriptions-item label="手机号">{{ profile.mobileMask || '—' }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card v-if="profile.educations && profile.educations.length" :bordered="false" class="profile-card" title="教育经历">
          <a-list :data-source="profile.educations" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <span class="list-main">{{ item.school }} · {{ item.major }}</span>
                <span class="list-sub">{{ item.degree }} · {{ item.startDate }} 至 {{ item.endDate }}</span>
              </a-list-item>
            </template>
          </a-list>
        </a-card>

        <a-card v-if="profile.works && profile.works.length" :bordered="false" class="profile-card" title="工作经历">
          <a-list :data-source="profile.works" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <span class="list-main">{{ item.company }} · {{ item.positionName }}</span>
                <span class="list-sub">{{ item.startDate }} 至 {{ item.endDate }}</span>
              </a-list-item>
            </template>
          </a-list>
        </a-card>

        <a-card v-if="profile.emergencies && profile.emergencies.length" :bordered="false" class="profile-card" title="紧急联系人">
          <a-list :data-source="profile.emergencies" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <span class="list-main">{{ item.name }}（{{ item.relation }}）</span>
                <span class="list-sub">{{ item.phone }}</span>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </template>

      <a-empty v-else-if="!loading" description="暂无档案信息" />
    </a-spin>
  </div>
</template>

<style scoped>
.my-profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.profile-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.mask-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}

.list-main {
  color: var(--color-text-primary);
}

.list-sub {
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
