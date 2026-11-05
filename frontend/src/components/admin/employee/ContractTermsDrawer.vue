<script setup lang="ts">
/**
 * 合同条款明细抽屉：条款列表 + 新增/编辑/删除（termType 下拉中文映射）。
 * 走 /emp/contracts/{id}/terms 契约，写操作需 emp:contract:write。
 * 由父组件以 v-if 控制挂载，挂载即加载。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../EmptyHint.vue';
import { errorText } from '../crud-helpers';
import {
  createContractTerm,
  deleteContractTerm,
  listContractTerms,
  updateContractTerm,
} from '../../../api/employee';
import { useUserStore } from '../../../stores/user';
import type { ContractTerm } from '../../../types/api-batch2';

const props = defineProps<{
  contractId: number;
  /** 抽屉标题附带的上下文（如工号-姓名） */
  context?: string;
}>();

const emit = defineEmits<{ (e: 'close'): void }>();

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('emp:contract:write'));

const termTypeLabels: Record<string, string> = {
  PROBATION: '试用期',
  SALARY: '薪酬',
  CONFIDENTIALITY: '保密',
  NON_COMPETE: '竞业限制',
  CUSTOM: '自定义',
};
const termTypeOptions = Object.entries(termTypeLabels).map(([value, label]) => ({ value, label }));

const columns = [
  { title: '条款类型', dataIndex: 'termType', key: 'termType', width: 110 },
  { title: '条款内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '生效日', dataIndex: 'effectiveFrom', key: 'effectiveFrom', width: 110 },
  { title: '失效日', key: 'effectiveTo', width: 110 },
];

const tableColumns = computed<any[]>(() => {
  const cols: any[] = [...columns];
  if (canWrite.value) cols.push({ title: '操作', key: 'action', width: 110, fixed: 'right' });
  return cols;
});

const rows = ref<ContractTerm[]>([]);
const loading = ref(false);

const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const form = reactive({
  termType: undefined as string | undefined,
  content: '',
  effectiveFrom: '',
  effectiveTo: '',
});

const modalTitle = computed(() => (modalMode.value === 'create' ? '新增条款' : '编辑条款'));

function dateText(v?: string | null): string {
  return v ? String(v).slice(0, 10) : '长期';
}

async function load() {
  loading.value = true;
  try {
    rows.value = (await listContractTerms(props.contractId)) ?? [];
  } catch (e) {
    message.error(errorText(e, '条款加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  modalMode.value = 'create';
  editingId.value = null;
  Object.assign(form, { termType: undefined, content: '', effectiveFrom: '', effectiveTo: '' });
  modalOpen.value = true;
}

function openEdit(record: ContractTerm) {
  modalMode.value = 'edit';
  editingId.value = record.id;
  Object.assign(form, {
    termType: record.termType,
    content: record.content ?? '',
    effectiveFrom: record.effectiveFrom?.slice(0, 10) ?? '',
    effectiveTo: record.effectiveTo?.slice(0, 10) ?? '',
  });
  modalOpen.value = true;
}

async function handleSubmit() {
  if (!form.termType) {
    message.warning('请选择条款类型');
    return;
  }
  if (!form.content.trim()) {
    message.warning('请填写条款内容');
    return;
  }
  if (!form.effectiveFrom) {
    message.warning('请选择生效日');
    return;
  }
  submitting.value = true;
  try {
    const payload: Record<string, unknown> = {
      termType: form.termType,
      content: form.content.trim(),
      effectiveFrom: form.effectiveFrom,
    };
    if (form.effectiveTo) payload.effectiveTo = form.effectiveTo;
    if (modalMode.value === 'create') {
      await createContractTerm(props.contractId, payload);
      message.success('条款已新增');
    } else if (editingId.value !== null) {
      await updateContractTerm(props.contractId, editingId.value, payload);
      message.success('条款已保存');
    }
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '条款保存失败'));
  } finally {
    submitting.value = false;
  }
}

function confirmRemove(record: ContractTerm) {
  Modal.confirm({
    title: '确认删除条款',
    content: `确定删除「${termTypeLabels[record.termType] ?? record.termType}」条款吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteContractTerm(props.contractId, record.id);
        message.success('条款已删除');
        await load();
      } catch (e) {
        message.error(errorText(e, '条款删除失败'));
      }
    },
  });
}

onMounted(load);
</script>

<template>
  <a-drawer
    open
    :title="`条款明细${context ? `（${context}）` : ''}`"
    width="640"
    @close="emit('close')"
  >
    <div class="toolbar">
      <a-button v-if="canWrite" type="primary" size="small" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新增条款
      </a-button>
    </div>

    <a-table
      v-if="rows.length"
      row-key="id"
      size="small"
      :columns="tableColumns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'termType'">
          <a-tag :color="record.termType === 'CUSTOM' ? 'default' : 'blue'">
            {{ termTypeLabels[record.termType] ?? record.termType }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'effectiveTo'">{{ dateText(record.effectiveTo) }}</template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="confirmRemove(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    <EmptyHint
      v-else-if="!loading"
      description="暂无条款"
      :hint="canWrite ? '点击右上角「新增条款」补充约定' : ''"
    />

    <a-modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form layout="vertical">
        <a-form-item label="条款类型" required>
          <a-select v-model:value="form.termType" :options="termTypeOptions" placeholder="请选择条款类型" />
        </a-form-item>
        <a-form-item label="条款内容" required>
          <a-textarea v-model:value="form.content" :rows="4" placeholder="条款约定内容" />
        </a-form-item>
        <a-form-item label="生效日" required>
          <a-date-picker v-model:value="form.effectiveFrom" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="失效日（不填为长期有效）">
          <a-date-picker v-model:value="form.effectiveTo" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-drawer>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
</style>
