<script setup lang="ts">

/**
 * 管理端-Offer 列表：CRUD + 录用建档（onboard，跨域转员工预建档）
 * + 发送（EMAIL/WECOM/DINGTALK，结果 SENT/FAILED）+ 回执登记（ACCEPTED/DECLINED），
 * 走 EntityCrudPage 组件 + /rcu/offers 接口。
 */
import { computed, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import OfferSendModal from '../../../components/admin/recruit/OfferSendModal.vue';
import OfferReceiptModal from '../../../components/admin/recruit/OfferReceiptModal.vue';
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
const canWrite = computed(() => userStore.hasPerm('rcu:offer:write'));

const sendOpen = ref(false);
const receiptOpen = ref(false);
const currentOffer = ref<CrudRow | null>(null);
/** 发送/回执成功后递增，靠重挂载刷新通用列表（避免改通用组件内部接口） */
const refreshKey = ref(0);

function openSend(record: CrudRow) {
  currentOffer.value = record;
  sendOpen.value = true;
}

function openReceipt(record: CrudRow) {
  currentOffer.value = record;
  receiptOpen.value = true;
}

const columns: CrudColumn[] = [
  { key: 'offerNo', title: 'Offer 编号', width: 160 },
  { key: 'applicationId', title: '投递 ID', width: 110 },
  { key: 'salaryAmountMask', title: '薪资金额(脱敏)', width: 150 },
  { key: 'status', title: '状态', width: 110, type: 'tag' },
  { key: 'receiptType', title: '回执', width: 110, type: 'tag' },
  { key: 'receiptAt', title: '回执时间', width: 160, type: 'datetime' },
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
      { label: '草稿', value: 'DRAFT' },
      { label: '待确认', value: 'PENDING' },
      { label: '已发送', value: 'SENT' },
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
    :key="refreshKey"
    title="Offer 管理"
    subtitle="维护 Offer 与录用结果；支持多渠道发送、候选人回执与录用建档"
    perm-prefix="rcu:offer"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record, reload }">
      <a-button
        v-if="canWrite && record.status === 'DRAFT'"
        type="link"
        size="small"
        @click="openSend(record)"
      >发送</a-button>
      <a-button
        v-if="canWrite && record.status !== 'DRAFT'"
        type="link"
        size="small"
        @click="openReceipt(record)"
      >回执</a-button>
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

  <OfferSendModal v-model:open="sendOpen" :offer="currentOffer" @done="refreshKey++" />
  <OfferReceiptModal v-model:open="receiptOpen" :offer="currentOffer" @done="refreshKey++" />
</template>
