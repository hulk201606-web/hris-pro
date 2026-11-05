<script setup lang="ts">

/**
 * 管理端-职位需求列表：CRUD（需求编号 / 部门 / 岗位 / 招聘人数），
 * 走 EntityCrudPage 组件 + /rcu/requisitions 接口。
 * 行内额外提供「发布官网 / 撤下」（RCS-F-004），成功后刷新列表同步 published 状态。
 */
import { computed } from 'vue';
import { message } from 'ant-design-vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  createRequisition,
  deleteRequisition,
  listRequisitions,
  publishRequisition,
  unpublishRequisition,
  updateRequisition,
} from '../../../api/recruit';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../../types/admin';
import { useUserStore } from '../../../stores/user';

const userStore = useUserStore();
const canPublish = computed(() => userStore.hasPerm('rcu:requisition:write'));

const columns: CrudColumn[] = [
  { key: 'reqNo', title: '需求编号', width: 160 },
  { key: 'deptId', title: '部门 ID', width: 110 },
  { key: 'positionId', title: '岗位 ID', width: 110 },
  { key: 'headcount', title: '招聘人数', width: 110, type: 'number' },
  { key: 'status', title: '状态', width: 120, type: 'tag' },
  { key: 'published', title: '官网发布', width: 100, type: 'tag', valueMap: { '1': '已发布', '0': '未发布' } },
  { key: 'reason', title: '需求原因' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'reqNo', label: '需求编号', type: 'text', required: true, placeholder: '如 REQ-2026-0014' },
  { key: 'deptId', label: '部门 ID', type: 'number', required: true, min: 1 },
  { key: 'positionId', label: '岗位 ID', type: 'number', required: true, min: 1 },
  { key: 'headcount', label: '招聘人数', type: 'number', required: true, min: 1 },
  { key: 'reason', label: '需求原因', type: 'textarea' },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '待审批', value: 'PENDING' },
      { label: '已审批', value: 'APPROVED' },
      { label: '已关闭', value: 'CLOSED' },
    ],
  },
];

const api: CrudApi = {
  list: listRequisitions,
  create: createRequisition,
  update: updateRequisition,
  remove: deleteRequisition,
};

async function togglePublish(record: CrudRow, reload: () => Promise<void>) {
  const published = Number(record.published) === 1;
  try {
    if (published) {
      await unpublishRequisition(Number(record.id));
      message.success('已撤下官网发布');
    } else {
      await publishRequisition(Number(record.id));
      message.success('已发布到官网招聘页');
    }
    await reload();
  } catch (e) {
    message.error(errorText(e, published ? '撤下失败' : '发布失败'));
  }
}
</script>

<template>
  <EntityCrudPage
    title="职位需求"
    subtitle="维护招聘需求单，审批通过后发布官网进入候选人筛选"
    perm-prefix="rcu:requisition"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record, reload }">
      <a-button
        v-if="canPublish"
        type="link"
        size="small"
        @click="togglePublish(record, reload)"
      >
        {{ Number(record.published) === 1 ? '撤下' : '发布官网' }}
      </a-button>
    </template>
  </EntityCrudPage>
</template>
