<script setup lang="ts">
/**
 * 管理端-字典项：先选字典类型，再维护该类型下的字典项（value / label / 排序）。
 *
 * 接口形态与常规不同：字典项挂在类型下，读取用
 * GET /plt/dicts/types/{typeCode}/items，但更新与删除用 PUT/DELETE
 * /plt/dicts/items/{id}（路径不带 typeCode）。因此本页不复用 SimpleCrudPage，
 * 自行管理「选类型 → 加载项 → 增改删」的状态。
 */
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  createDictItem,
  deleteDictItem,
  listDictItems,
  listDictTypes,
  updateDictItem,
} from '../../../api/platform';
import type { DictItemResponse, DictTypeResponse } from '../../../types/api';

const types = ref<DictTypeResponse[]>([]);
const items = ref<DictItemResponse[]>([]);
const selectedType = ref<string | undefined>(undefined);
const loading = ref(false);
const submitting = ref(false);
const modalOpen = ref(false);
const editingId = ref<number | null>(null);

const form = reactive({ value: '', label: '', sort: 0 });

async function loadTypes() {
  try {
    types.value = await listDictTypes();
    if (types.value.length && !selectedType.value) {
      selectedType.value = types.value[0].code;
      await loadItems();
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '字典类型加载失败');
  }
}

async function loadItems() {
  if (!selectedType.value) {
    items.value = [];
    return;
  }
  loading.value = true;
  try {
    items.value = await listDictItems(selectedType.value);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '字典项加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (!selectedType.value) return message.warning('请先选择字典类型');
  editingId.value = null;
  form.value = '';
  form.label = '';
  form.sort = items.value.length + 1;
  modalOpen.value = true;
}

function openEdit(row: DictItemResponse) {
  editingId.value = row.id;
  form.value = row.value;
  form.label = row.label;
  form.sort = row.sort;
  modalOpen.value = true;
}

async function handleSubmit() {
  if (!selectedType.value) return;
  if (!form.value.trim()) return message.warning('请填写字典值');
  if (!form.label.trim()) return message.warning('请填写显示文案');
  submitting.value = true;
  try {
    if (editingId.value) {
      await updateDictItem(editingId.value, { ...form });
      message.success('已更新');
    } else {
      await createDictItem(selectedType.value, { ...form });
      message.success('已新增');
    }
    modalOpen.value = false;
    await loadItems();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: DictItemResponse) {
  try {
    await deleteDictItem(row.id);
    message.success('已删除');
    await loadItems();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败');
  }
}

onMounted(loadTypes);
</script>

<template>
  <div class="dict-item">
    <section class="head">
      <h2 class="head-title">字典项</h2>
      <p class="head-sub">字典项按类型归属；显示文案与业务值分离，修改文案不影响已存数据。</p>
    </section>

    <a-space class="toolbar">
      <span>字典类型</span>
      <a-select
        v-model:value="selectedType"
        :options="types.map((t) => ({ value: t.code, label: `${t.name}（${t.code}）` }))"
        style="width: 280px"
        placeholder="请选择类型"
        @change="loadItems"
      />
      <a-button type="primary" :disabled="!selectedType" @click="openCreate">新增字典项</a-button>
    </a-space>

    <a-table
      :data-source="items"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="middle"
      :locale="{ emptyText: '该类型下暂无字典项' }"
    >
      <a-table-column key="value" title="字典值" data-index="value" :width="200" />
      <a-table-column key="label" title="显示文案" data-index="label" :width="200" />
      <a-table-column key="sort" title="排序" data-index="sort" :width="90" />
      <a-table-column key="actions" title="操作" :width="140">
        <template #default="{ record }">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
          <a-popconfirm title="确认删除该字典项？" @confirm="handleDelete(record)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </a-table-column>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑字典项' : '新增字典项'"
      :confirm-loading="submitting"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="字典值" required>
          <a-input v-model:value="form.value" placeholder="存库的值，如 FULLTIME" />
        </a-form-item>
        <a-form-item label="显示文案" required>
          <a-input v-model:value="form.label" placeholder="界面显示，如 全职" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.sort" :min="0" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.dict-item {
  padding: 4px;
}
.head {
  margin-bottom: 16px;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.head-sub {
  margin: 0;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
