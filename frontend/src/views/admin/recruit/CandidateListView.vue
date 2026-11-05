<script setup lang="ts">

/**
 * 管理端-候选人列表：CRUD（姓名 / 脱敏手机号 / 邮箱 / 阶段），
 * 走 EntityCrudPage 组件 + /rcu/candidates 接口。
 * 行操作「解析结果」：打开简历解析抽屉（GET/POST/PUT /rcu/resumes/{id}/parse）。
 */
import { computed, ref } from 'vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import ResumeParseDrawer from '../../../components/admin/recruit/ResumeParseDrawer.vue';
import {
  createCandidate,
  deleteCandidate,
  listCandidates,
  updateCandidate,
} from '../../../api/recruit';
import { useUserStore } from '../../../stores/user';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const userStore = useUserStore();
// 解析查看走 rcu:resume:read（随页面读权限开放），重新解析/人工修正需 rcu:resume:write
const canWrite = computed(() => userStore.hasPerm('rcu:resume:write'));

// 候选人行不携带 rcu_resume.id，抽屉内输入简历 ID 定位（后端候选人与简历暂无联动查询）
const parseDrawerOpen = ref(false);

const columns: CrudColumn[] = [
  { key: 'name', title: '姓名', width: 140 },
  { key: 'mobileMask', title: '手机号(脱敏)', width: 150 },
  { key: 'email', title: '邮箱' },
  { key: 'status', title: '阶段', width: 130, type: 'tag' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '姓名', type: 'text', required: true },
  { key: 'mobileCipher', label: '手机号', type: 'text', placeholder: 'MVP 阶段密文列存明文' },
  { key: 'mobileMask', label: '手机号(脱敏)', type: 'text', placeholder: '如 138****1234' },
  { key: 'email', label: '邮箱', type: 'text' },
  {
    key: 'status',
    label: '阶段',
    type: 'select',
    required: true,
    options: [
      { label: '筛选中', value: 'SCREENING' },
      { label: '面试中', value: 'INTERVIEWING' },
      { label: 'Offer 中', value: 'OFFER' },
      { label: '已录用', value: 'HIRED' },
      { label: '已淘汰', value: 'REJECTED' },
    ],
  },
];

const api: CrudApi = {
  list: listCandidates,
  create: createCandidate,
  update: updateCandidate,
  remove: deleteCandidate,
};
</script>

<template>
  <EntityCrudPage
    title="候选人"
    subtitle="维护候选人主档，跟踪筛选、面试到录用的阶段流转"
    perm-prefix="rcu:candidate"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions>
      <a-button type="link" size="small" @click="parseDrawerOpen = true">
        解析结果
      </a-button>
    </template>
  </EntityCrudPage>

  <ResumeParseDrawer
    v-if="parseDrawerOpen"
    :can-write="canWrite"
    @close="parseDrawerOpen = false"
  />
</template>
