<script setup lang="ts">

/**
 * 管理端-薪酬取数周期列表：CRUD + 封存动作（close，封存后不可变更），
 * 走 SimpleCrudPage 组件 + /att/payroll-cycles 接口。
 */
import { computed } from 'vue';
import { Modal, message } from 'ant-design-vue';
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import {
  closePayrollCycle,
  createPayrollCycle,
  listPayrollCycles,
  updatePayrollCycle,
} from '../../../api/attendance';
import { errorText } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import type { CrudColumn, CrudField, CrudRow, SimpleCrudApi } from '../../../types/admin';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('att:cycle:write'));

const columns: CrudColumn[] = [
  { key: 'periodCode', title: '周期编码', width: 130 },
  { key: 'startDate', title: '开始日期', width: 130, type: 'date' },
  { key: 'endDate', title: '结束日期', width: 130, type: 'date' },
  { key: 'status', title: '状态', width: 110, type: 'tag', valueMap: { OPEN: '进行中', CLOSED: '已封存' } },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'periodCode', label: '周期编码', type: 'text', required: true, placeholder: '如 2026-11' },
  { key: 'startDate', label: '开始日期', type: 'date', required: true },
  { key: 'endDate', label: '结束日期', type: 'date', required: true },
];

const api: SimpleCrudApi = {
  list: listPayrollCycles,
  create: createPayrollCycle,
  update: updatePayrollCycle,
};

function handleClose(record: CrudRow, reload?: () => Promise<void> | void) {
  Modal.confirm({
    title: '封存周期',
    content: `确认封存考勤周期「${record.periodCode}」？封存后不可再变更，将作为薪酬取数依据。`,
    okText: '封存',
    cancelText: '取消',
    async onOk() {
      try {
        await closePayrollCycle(Number(record.id));
        message.success('已封存');
        if (reload) await reload();
      } catch (e) {
        message.error(errorText(e, '封存失败'));
      }
    },
  });
}
</script>

<template>
  <SimpleCrudPage
    title="薪酬取数周期"
    subtitle="维护月度考勤周期（支持非自然月切分），封存后供薪酬服务取数"
    perm-prefix="att:cycle"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record, reload }">
      <a-button
        v-if="canWrite && record.status !== 'CLOSED'"
        type="link"
        size="small"
        @click="handleClose(record, reload)"
      >
        封存
      </a-button>
      <a-tag v-else-if="record.status === 'CLOSED'" color="default">已封存</a-tag>
    </template>
  </SimpleCrudPage>
</template>
