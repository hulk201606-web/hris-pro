<script setup lang="ts">
/**
 * 管理端-敏感词词库（KBD-F-017）：列表 / 新增 / 删除。
 * level 仅 BLOCK（直接拒绝入库）与 WARN（入库并回传 warnings）；复用 kb:document 权限码。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  createSensitiveWord,
  deleteSensitiveWord,
  listSensitiveWords,
} from '../../../api/knowledge';
import { useUserStore } from '../../../stores/user';
import type { SensitiveWordRow } from '../../../types/api-batch9';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('kb:document:write'));

const rows = ref<SensitiveWordRow[]>([]);
const loading = ref(false);
const submitting = ref(false);
const modalOpen = ref(false);
const form = reactive({ word: '', level: 'WARN' as 'BLOCK' | 'WARN' });

async function load() {
  loading.value = true;
  try {
    rows.value = await listSensitiveWords();
  } catch (e) {
    message.error(errorText(e, '敏感词列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  form.word = '';
  form.level = 'WARN';
  modalOpen.value = true;
}

async function handleSubmit() {
  const word = form.word.trim();
  if (!word) return message.warning('请填写敏感词');
  if (word.length > 64) return message.warning('敏感词长度不能超过 64');
  submitting.value = true;
  try {
    await createSensitiveWord(word, form.level);
    message.success('已新增');
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '新增失败'));
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: SensitiveWordRow) {
  try {
    await deleteSensitiveWord(row.id);
    message.success('已删除');
    await load();
  } catch (e) {
    message.error(errorText(e, '删除失败'));
  }
}

onMounted(load);
</script>

<template>
  <div class="word-page">
    <section class="head">
      <div>
        <h2 class="head-title">敏感词词库</h2>
        <p class="head-sub">BLOCK 命中直接拒绝文档入库；WARN 命中放行并在保存结果 warnings 中提示</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新增敏感词</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 20, hideOnSinglePage: true }"
        :scroll="{ x: 600 }"
      >
        <a-table-column key="word" title="敏感词" data-index="word" />
        <a-table-column key="level" title="级别" :width="140">
          <template #default="{ record }">
            <a-tag :color="record.level === 'BLOCK' ? 'red' : 'gold'">
              {{ record.level === 'BLOCK' ? '拦截' : '告警' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column key="createdAt" title="创建时间" :width="180">
          <template #default="{ record }">
            {{ record.createdAt ? String(record.createdAt).replace('T', ' ').slice(0, 16) : '—' }}
          </template>
        </a-table-column>
        <a-table-column key="action" title="操作" :width="100">
          <template #default="{ record }">
            <a-popconfirm
              v-if="canWrite"
              title="确认删除该敏感词？"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
            <span v-else class="muted">—</span>
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="词库为空" hint="新增敏感词后对文档与切片生效" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      title="新增敏感词"
      :confirm-loading="submitting"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="敏感词" required>
          <a-input v-model:value="form.word" :maxlength="64" placeholder="如 违规承诺" />
        </a-form-item>
        <a-form-item label="级别" required>
          <a-radio-group v-model:value="form.level">
            <a-radio value="BLOCK">BLOCK（拦截入库）</a-radio>
            <a-radio value="WARN">WARN（仅告警）</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.word-page { display: flex; flex-direction: column; gap: var(--space-6); }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); }
.head-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--color-text-primary); }
.head-sub { margin: var(--space-2) 0 0; font-size: 14px; color: var(--color-text-secondary); }
.table-card { border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.muted { color: var(--color-text-muted); }
</style>
