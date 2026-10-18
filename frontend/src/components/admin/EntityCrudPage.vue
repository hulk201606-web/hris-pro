<script setup lang="ts">
import { computed, onMounted, reactive, ref, useSlots } from 'vue';
import { Modal, message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import { errorText, formatCell, resolveTagColor } from './crud-helpers';
import EmptyHint from './EmptyHint.vue';
import { useUserStore } from '../../stores/user';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../types/admin';

/**
 * 通用分页 CRUD 页：列表 + 新建/编辑弹窗 + 删除确认。
 * - 列渲染类型（文本/标签/日期/数值）由 columns 声明；
 * - 表单控件类型（文本/数字/日期/下拉/多行）由 fields 声明；
 * - 行内额外操作通过 #rowActions 插槽注入（如 Offer 录用建档）。
 */
const props = defineProps<{
  title: string;
  subtitle?: string;
  permPrefix: string;
  columns: CrudColumn[];
  fields: CrudField[];
  api: CrudApi;
}>();

const slots = useSlots();
const userStore = useUserStore();
const rows = ref<CrudRow[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formState = reactive<Record<string, any>>({});

const canWrite = computed(() => userStore.hasPerm(`${props.permPrefix}:write`));
const hasExtraActions = computed(() => Boolean(slots.rowActions));
const showActions = computed(
  () => hasExtraActions.value || Boolean((props.api.update && canWrite.value) || (props.api.remove && canWrite.value)),
);

const colMap = computed<Record<string, CrudColumn>>(() => {
  const map: Record<string, CrudColumn> = {};
  props.columns.forEach((c) => {
    map[c.key] = c;
  });
  return map;
});

const tableColumns = computed<any[]>(() => {
  const cols: any[] = props.columns.map((c) => ({ title: c.title, dataIndex: c.key, key: c.key, width: c.width }));
  if (showActions.value) cols.push({ title: '操作', key: 'action', width: 180, fixed: 'right' });
  return cols;
});

const modalTitle = computed(() => `${modalMode.value === 'create' ? '新建' : '编辑'}${props.title}`);

function isTag(key: string): boolean {
  return colMap.value[key]?.type === 'tag';
}

async function load() {
  loading.value = true;
  try {
    const page = await props.api.list(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '数据加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function onTableChange(pag: { current?: number; pageSize?: number }) {
  if (pag.current) pagination.current = pag.current;
  if (pag.pageSize) pagination.pageSize = pag.pageSize;
  load();
}

function clearForm() {
  Object.keys(formState).forEach((k) => delete formState[k]);
}

function openCreate() {
  clearForm();
  props.fields.forEach((f) => {
    formState[f.key] = f.type === 'number' || f.type === 'select' ? undefined : '';
  });
  modalMode.value = 'create';
  editingId.value = null;
  modalOpen.value = true;
}

function openEdit(record: CrudRow) {
  clearForm();
  props.fields.forEach((f) => {
    formState[f.key] = record[f.key] ?? (f.type === 'number' || f.type === 'select' ? undefined : '');
  });
  modalMode.value = 'edit';
  editingId.value = Number(record.id);
  modalOpen.value = true;
}

function validate(): string | null {
  for (const f of props.fields) {
    if (!f.required) continue;
    const v = formState[f.key];
    if (v === undefined || v === null || v === '') return `请填写「${f.label}」`;
  }
  return null;
}

async function handleSubmit() {
  const invalid = validate();
  if (invalid) {
    message.warning(invalid);
    return;
  }
  submitting.value = true;
  try {
    const payload: Record<string, unknown> = {};
    props.fields.forEach((f) => {
      const v = formState[f.key];
      if (v !== undefined && v !== null && v !== '') payload[f.key] = v;
    });
    if (modalMode.value === 'create') {
      await props.api.create?.(payload);
      message.success('创建成功');
    } else if (editingId.value !== null) {
      await props.api.update?.(editingId.value, payload);
      message.success('保存成功');
    }
    modalOpen.value = false;
    pagination.current = 1;
    await load();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

function handleDelete(record: CrudRow) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后不可恢复，确定继续吗？',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await props.api.remove?.(Number(record.id));
        message.success('已删除');
        await load();
      } catch (e) {
        message.error(errorText(e, '删除失败'));
      }
    },
  });
}

onMounted(load);
</script>

<template>
  <div class="crud-page">
    <section class="head">
      <div>
        <h2 class="head-title">{{ title }}</h2>
        <p v-if="subtitle" class="head-sub">{{ subtitle }}</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite && api.create" type="primary" @click="openCreate">新建</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 900 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="api.update && canWrite" type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <slot name="rowActions" :record="record" :reload="load" />
              <a-button v-if="api.remove && canWrite" type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
          <template v-else-if="isTag(column.key)">
            <a-tag :color="resolveTagColor(record[column.key])">{{ formatCell(colMap[column.key], record[column.key]) }}</a-tag>
          </template>
          <template v-else>
            {{ formatCell(colMap[column.key], record[column.key]) }}
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无数据" :hint="canWrite && api.create ? '点击右上角「新建」添加第一条记录' : ''" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form :model="formState" layout="vertical">
        <a-form-item v-for="f in fields" :key="f.key" :label="f.label" :required="f.required">
          <a-input
            v-if="f.type === 'text'"
            v-model:value="formState[f.key]"
            :placeholder="f.placeholder ?? `请输入${f.label}`"
          />
          <a-textarea
            v-else-if="f.type === 'textarea'"
            v-model:value="formState[f.key]"
            :rows="3"
            :placeholder="f.placeholder ?? `请输入${f.label}`"
          />
          <a-input-number
            v-else-if="f.type === 'number'"
            v-model:value="formState[f.key]"
            :min="f.min"
            style="width: 100%"
          />
          <a-date-picker
            v-else-if="f.type === 'date'"
            v-model:value="formState[f.key]"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <a-select
            v-else-if="f.type === 'select'"
            v-model:value="formState[f.key]"
            :options="f.options"
            allow-clear
            :placeholder="f.placeholder ?? `请选择${f.label}`"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.crud-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
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

.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
