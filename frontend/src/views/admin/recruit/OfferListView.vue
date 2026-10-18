<script setup lang="ts">
import { computed } from 'vue';
import { Modal, message } from 'ant-design-vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createOffer,
  deleteOffer,
  listOffers,
  onboardOffer,
  updateOffer,
} from '../../../api/recruit';
import { BizError } from '../../../api/error';
import { useUserStore } from '../../../stores/user';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../../types/admin';

const userStore = useUserStore();
const canOnboard = computed(() => userStore.hasPerm('rcu:offer:onboard'));

const columns: CrudColumn[] = [
  { key: 'offerNo', title: 'Offer 编号', width: 160 },
  { key: 'applicationId', title: '投递 ID', width: 110 },
  { key: 'salaryAmountMask', title: '薪资金额(脱敏)', width: 150 },
  { key: 'status', title: '状态', width: 120, type: 'tag' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'applicationId', label: '投递 ID', type: 'number', required: true, min: 1 },
  { key: 'offerNo', label: 'Offer 编号', type: 'text', required: true, placeholder: '如 OFF-2026-0033' },
  { key: 'salaryAmountCipher', label: '薪资金额', type: 'text', placeholder: 'MVP 阶段密文列存明文' },
  { key: 'salaryAmountMask', label: '薪资金额(脱敏)', type: 'text', placeholder: '如 25****' },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '待确认', value: 'PENDING' },
      { label: '已接受', value: 'ACCEPTED' },
      { label: '已拒绝', value: 'REJECTED' },
      { label: '已过期', value: 'EXPIRED' },
    ],
  },
  { key: 'procInstId', label: '流程实例 ID', type: 'text' },
];

const api: CrudApi = {
  list: listOffers,
  create: createOffer,
  update: updateOffer,
  remove: deleteOffer,
};

function handleOnboard(record: CrudRow, reload?: () => Promise<void> | void) {
  Modal.confirm({
    title: '录用建档',
    content: `确认录用 Offer「${record.offerNo}」并同步创建员工档案？该操作将标记 Offer 为已接受。`,
    okText: '确认录用',
    cancelText: '取消',
    async onOk() {
      try {
        const empId = await onboardOffer(Number(record.id));
        message.success(`已录用并创建员工档案，员工 ID：${empId}`);
        if (reload) await reload();
      } catch (e) {
        message.error(e instanceof BizError ? e.message : '录用建档失败');
      }
    },
  });
}
</script>

<template>
  <EntityCrudPage
    title="Offer 管理"
    subtitle="维护 Offer 与录用结果；录用后将跨域创建员工预建档"
    perm-prefix="rcu:offer"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record, reload }">
      <a-button
        v-if="canOnboard && record.status !== 'ACCEPTED'"
        type="link"
        size="small"
        @click="handleOnboard(record, reload)"
      >
        录用建档
      </a-button>
      <a-tag v-else-if="record.status === 'ACCEPTED'" color="green">已录用</a-tag>
    </template>
  </EntityCrudPage>
</template>
