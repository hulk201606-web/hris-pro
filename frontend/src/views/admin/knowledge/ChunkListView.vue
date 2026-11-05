<script setup lang="ts">
/**
 * 管理端-知识切片（KBD-F-017）：切片列表 / 新建 / 删除。
 * 新建响应 warnings 非空时在页顶展示敏感词告警；BLOCK 级命中由后端直接拒绝。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { createChunk, deleteChunk, listChunks } from '../../../api/knowledge';
import { useUserStore } from '../../../stores/user';
import type { ChunkRow } from '../../../types/api-batch9';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('kb:chunk:write'));

const rows = ref<ChunkRow[]>([]);
const loading = ref(false);
const submitting = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });
const warnings = ref<string[]>([]);

const modalOpen = ref(false);
const form = reactive({
  docVersionId: undefined as number | undefined,
  spaceId: undefined as number | undefined,
  seq: 1,
  headingPath: '',
  text: '',
  tokenCnt: 1,
});

async function load() {
  loading.value = true;
  try {
    const page = await listChunks(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '切片列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function onTableChange(p: { current?: number; pageSize?: number }) {
  if (p.current) pagination.current = p.current;
  if (p.pageSize) pagination.pageSize = p.pageSize;
  load();
}

function openCreate() {
  Object.assign(form, { docVersionId: undefined, spaceId: undefined, seq: 1, headingPath: '', text: '', tokenCnt: 1 });
  modalOpen.value = true;
}

async function handleSubmit() {
  if (!form.docVersionId || !form.spaceId) return message.warning('请填写文档版本 ID 与空间 ID');
  if (!form.text.trim()) return message.warning('请填写切片正文');
  submitting.value = true;
  try {
    const created = await createChunk({
      docVersionId: form.docVersionId,
      spaceId: form.spaceId,
      seq: form.seq,
      headingPath: form.headingPath.trim() || undefined,
      text: form.text.trim(),
      tokenCnt: form.tokenCnt,
    });
    warnings.value = created.warnings ?? [];
    message.success('切片已保存');
    modalOpen.value = false;
    pagination.current = 1;
    await load();
  } catch (e) {
    message.error(errorText(e, '切片保存失败'));
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: ChunkRow) {
  try {
    await deleteChunk(row.id);
    message.success('已删除');
    await load();
  } catch (e) {
    message.error(errorText(e, '删除失败'));
  }
}

onMounted(load);
</script>

<template>
  <div class="chunk-page">
    <section class="head">
      <div>
        <h2 class="head-title">知识切片</h2>
        <p class="head-sub">切片入库时执行敏感词校验：BLOCK 拒绝，WARN 放行并在结果中提示</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建切片</a-button>
      </a-space>
    </section>

    <a-alert
      v-if="warnings.length"
      type="warning"
      show-icon
      closable
      class="warn-bar"
      message="切片已保存，但命中敏感词告警"
      :description="`命中词：${warnings.join('、')}`"
      @close="warnings = []"
    />

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1000 }"
        @change="onTableChange"
      >
        <a-table-column key="id" title="ID" data-index="id" :width="80" />
        <a-table-column key="docVersionId" title="文档版本 ID" data-index="docVersionId" :width="120" />
        <a-table-column key="spaceId" title="空间 ID" data-index="spaceId" :width="100" />
        <a-table-column key="seq" title="序号" data-index="seq" :width="80" />
        <a-table-column key="headingPath" title="标题路径" :width="180">
          <template #default="{ record }">{{ record.headingPath || '—' }}</template>
        </a-table-column>
        <a-table-column key="text" title="正文">
          <template #default="{ record }">
            <span class="clip">{{ record.text || '—' }}</span>
          </template>
        </a-table-column>
        <a-table-column key="tokenCnt" title="Token" data-index="tokenCnt" :width="90" />
        <a-table-column key="warnings" title="告警" :width="160">
          <template #default="{ record }">
            <a-tag v-if="record.warnings?.length" color="gold">{{ record.warnings.join('、') }}</a-tag>
            <span v-else class="muted">无</span>
          </template>
        </a-table-column>
        <a-table-column key="action" title="操作" :width="90" fixed="right">
          <template #default="{ record }">
            <a-popconfirm v-if="canWrite" title="确认删除该切片？" @confirm="handleDelete(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="暂无切片" hint="文档解析分块后在此维护切片正文" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      title="新建切片"
      :confirm-loading="submitting"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="文档版本 ID" required>
          <a-input-number v-model:value="form.docVersionId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="空间 ID" required>
          <a-input-number v-model:value="form.spaceId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="序号">
          <a-input-number v-model:value="form.seq" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="标题路径">
          <a-input v-model:value="form.headingPath" placeholder="选填，如 第三章 / 3.2 请假" />
        </a-form-item>
        <a-form-item label="正文" required>
          <a-textarea v-model:value="form.text" :rows="4" placeholder="切片正文内容" />
        </a-form-item>
        <a-form-item label="Token 数">
          <a-input-number v-model:value="form.tokenCnt" :min="1" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.chunk-page { display: flex; flex-direction: column; gap: var(--space-6); }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); }
.head-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--color-text-primary); }
.head-sub { margin: var(--space-2) 0 0; font-size: 14px; color: var(--color-text-secondary); }
.table-card { border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.warn-bar { border-radius: var(--radius-lg); }
.clip { display: inline-block; max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.muted { color: var(--color-text-muted); }
</style>
