<script setup lang="ts">
import { computed, onMounted, reactive, ref, useSlots } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import { errorText, formatCell, resolveTagColor } from './crud-helpers';
import EmptyHint from './EmptyHint.vue';
import { useUserStore } from '../../stores/user';
import type { CrudColumn, CrudField, CrudRow, SimpleCrudApi } from '../../types/admin';

/**
 * 非分页 CRUD 页：用于返回 List 的配置类接口（考勤组/班次/假种/周期）。
 * 表格分页由前端承接，仅支持新建/编辑，行内额外动作走 #rowActions 插槽。
 */
const props = defineProps<{
  title: string;
  subtitle?: string;
  permPrefix: string;
  columns: CrudColumn[];
  fields: CrudField[];
  api: SimpleCrudApi;
}>();

const slots = useSlots();
const userStore = useUserStore();

const rows = ref<CrudRow[]>([]);
const loading = ref(false);
const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formState = reactive<Record<string, any>>({});

const canWrite = computed(() => userStore.hasPerm(`${props.permPrefix}:write`));
const showActions = computed(
  () => Boolean(slots.rowActions) || Boolean(props.api.update && canWrite.value),
);
const modalTitle = computed(() => `${modalMode.value === 'create' ? '新建' : '编辑'}${props.title}`);

const colMap = computed<Record<string, CrudColumn>>(() => {
  const map: Record<string, CrudColumn> = {};
  props.columns.forEach((c) => {
    map[c.key] = c;
  });
  return map;
});

const tableColumns = computed<any[]>(() => {
  const cols: any[] = props.columns.map((c) => ({ title: c.title, dataIndex: c.key, key: c.key, width: c.width }));
  if (showActions.value) cols.push({ title: '操作', key: 'action', width: 160, fixed: 'right' });
  return cols;
});

function isTag(key: string): boolean {
  return colMap.value[key]?.type === 'tag';
}

function defaultValue(f: CrudField): unknown {
  // 开关默认开启（后端布尔字段普遍缺省 true，如考勤组 requireLocation）
  if (f.type === 'switch') return true;
  return f.type === 'number' || f.type === 'select' || f.type === 'time' ? undefined : '';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await props.api.list();
  } catch (e) {
    message.error(errorText(e, '数据加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function clearForm() {
  Object.keys(formState).forEach((k) => delete formState[k]);
}

function openCreate() {
  clearForm();
  props.fields.forEach((f) => {
    formState[f.key] = defaultValue(f);
  });
  modalMode.value = 'create';
  editingId.value = null;
  modalOpen.value = true;
}

function openEdit(record: CrudRow) {
  clearForm();
  props.fields.forEach((f) => {
    formState[f.key] = record[f.key] ?? defaultValue(f);
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
    await load();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
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
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 820 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="api.update && canWrite" type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <slot name="rowActions" :record="record" :reload="load" />
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
          <a-time-picker
            v-else-if="f.type === 'time'"
            v-model:value="formState[f.key]"
            value-format="HH:mm:ss"
            format="HH:mm"
            style="width: 100%"
          />
          <a-select
            v-else-if="f.type === 'select'"
            v-model:value="formState[f.key]"
            :options="f.options"
            allow-clear
            :placeholder="f.placeholder ?? `请选择${f.label}`"
          />
          <a-switch v-else-if="f.type === 'switch'" v-model:checked="formState[f.key]" />
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
