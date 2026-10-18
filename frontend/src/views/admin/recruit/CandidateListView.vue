<script setup lang="ts">
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createCandidate,
  deleteCandidate,
  listCandidates,
  updateCandidate,
} from '../../../api/recruit';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

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
  />
</template>
