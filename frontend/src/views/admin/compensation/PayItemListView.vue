<script setup lang="ts">
/**
 * 管理端-薪资项管理页：按薪酬组过滤的薪资项列表 + 新建/编辑弹窗（PayItemFormModal）。
 * 权限：读 cmp:pay-item:read，写 cmp:pay-item:write；菜单显隐复用 payroll-run 资源。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import PayItemFormModal from '../../../components/admin/PayItemFormModal.vue';
import CompCalcSwitchCard from '../../../components/admin/compensation/CompCalcSwitchCard.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  deletePayItem,
  listPayGroups,
  listPayItems,
} from '../../../api/compensation';
import { useUserStore } from '../../../stores/user';
import type { PayGroup, PayItem } from '../../../types/api-batch3';

const userStore = useUserStore();
const canRead = computed(() => userStore.hasPerm('cmp:pay-item:read'));
const canWrite = computed(() => userStore.hasPerm('cmp:pay-item:write'));

const CATEGORY_COLORS: Record<string, string> = {
  EARNING: 'green',
  DEDUCTION: 'orange',
  TAX: 'purple',
  NET: 'blue',
};

const baseColumns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 140 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '类别', dataIndex: 'category', key: 'category', width: 110 },
  { title: '顺序', dataIndex: 'seq', key: 'seq', width: 70 },
  { title: '公式', dataIndex: 'formula', key: 'formula', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
];

const payGroups = ref<PayGroup[]>([]);
const payGroupId = ref<number | null>(null);
const rows = ref<PayItem[]>([]);
const loading = ref(false);

const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editing = ref<PayItem | null>(null);

const tableColumns = computed<any[]>(() => {
  const cols: any[] = [...baseColumns];
  if (canWrite.value) cols.push({ title: '操作', key: 'action', width: 130, fixed: 'right' });
  return cols;
});

async function loadPayGroups() {
  try {
    const page = await listPayGroups(1, 200);
    payGroups.value = page.records;
    if (page.records.length && !payGroupId.value) payGroupId.value = page.records[0].id;
  } catch (e) {
    message.error(errorText(e, '薪酬组列表加载失败'));
  }
}

async function load() {
  if (!canRead.value || !payGroupId.value) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value = await listPayItems(payGroupId.value);
  } catch (e) {
    message.error(errorText(e, '薪资项加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function dt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}

function openCreate() {
  modalMode.value = 'create';
  editing.value = null;
  modalOpen.value = true;
}

function openEdit(record: PayItem) {
  modalMode.value = 'edit';
  editing.value = record;
  modalOpen.value = true;
}

async function handleDelete(record: PayItem) {
  try {
    await deletePayItem(record.id);
    message.success('薪资项已删除');
    await load();
  } catch (e) {
    message.error(errorText(e, '删除失败'));
  }
}

onMounted(async () => {
  await loadPayGroups();
  await load();
});
</script>

<template>
  <div class="payitem-page">
    <section class="head">
      <div>
        <h2 class="head-title">薪资项管理</h2>
        <p class="head-sub">
          按薪酬组维护薪资项与算薪公式；公式为 Aviator 表达式，可用函数
          round/min/max/taxMonthly，可用变量 base/lateCount/absentDays 与前序项编码
        </p>
      </div>
      <a-button v-if="canWrite" type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新建薪资项
      </a-button>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="薪酬组">
          <a-select
            v-model:value="payGroupId"
            style="width: 240px"
            placeholder="选择薪酬组"
            show-search
            option-filter-prop="label"
            :options="payGroups.map((g) => ({ label: `${g.name} (${g.code})`, value: g.id }))"
            @change="load"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="load">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'category'">
            <a-tag :color="CATEGORY_COLORS[record.category] || 'default'">
              {{ record.category }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ dt(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-if="canWrite" type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm v-if="canWrite" title="确认删除该薪资项？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint
            description="该薪酬组暂无薪资项"
            :hint="canWrite ? '点击右上角「新建薪资项」添加第一个项目' : ''"
          />
        </template>
      </a-table>
    </a-card>

    <PayItemFormModal
      v-model:open="modalOpen"
      :mode="modalMode"
      :editing="editing"
      :pay-group-id="payGroupId"
      @saved="load"
    />

    <CompCalcSwitchCard />
  </div>
</template>

<style scoped>
.payitem-page {
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

.filter-card,
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filter-form {
  row-gap: var(--space-3);
}
</style>
