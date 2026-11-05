<script setup lang="ts">
/**
 * 管理端-简历库（RCS-F-022）：展示保留期 retentionUntil，并提供「清理过期」。
 * 清理为逻辑删除 retentionUntil < today 的记录并清空引用，返回本次清理条数。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import { ClearOutlined, ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { cleanupExpiredResumes, listResumes } from '../../../api/recruit';
import { useUserStore } from '../../../stores/user';
import type { ResumeRow } from '../../../types/api-batch9';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('rcu:resume:write'));

const rows = ref<ResumeRow[]>([]);
const loading = ref(false);
const cleaning = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

/** 本地日期（与后端 LocalDate 比较，避免 UTC 偏移导致过期判定差一天） */
function localDate(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

const today = localDate(new Date());

function expired(row: ResumeRow): boolean {
  return Boolean(row.retentionUntil && row.retentionUntil < today);
}

async function load() {
  loading.value = true;
  try {
    const page = await listResumes(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '简历列表加载失败'));
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

function handleCleanup() {
  Modal.confirm({
    title: '清理过期简历',
    content: '将逻辑删除保留期已过（retentionUntil < 今天）的简历并清空引用，操作不可撤销。',
    okText: '开始清理',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      cleaning.value = true;
      try {
        const res = await cleanupExpiredResumes();
        message.success(`清理完成，共处理 ${res.cleaned} 条过期简历`);
        pagination.current = 1;
        await load();
      } catch (e) {
        message.error(errorText(e, '清理失败'));
      } finally {
        cleaning.value = false;
      }
    },
  });
}

onMounted(load);
</script>

<template>
  <div class="resume-page">
    <section class="head">
      <div>
        <h2 class="head-title">简历库</h2>
        <p class="head-sub">按保留期管理候选人简历；到期记录可批量逻辑删除并清空引用</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" danger :loading="cleaning" @click="handleCleanup">
          <template #icon><ClearOutlined /></template>
          清理过期
        </a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 900 }"
        @change="onTableChange"
      >
        <a-table-column key="id" title="简历 ID" data-index="id" :width="100" />
        <a-table-column key="candidateId" title="候选人 ID" data-index="candidateId" :width="110" />
        <a-table-column key="fileId" title="文件 ID" :width="110">
          <template #default="{ record }">{{ record.fileId ?? '—' }}</template>
        </a-table-column>
        <a-table-column key="retentionUntil" title="保留至" :width="150">
          <template #default="{ record }">
            <span>{{ record.retentionUntil ?? '未设置' }}</span>
            <a-tag v-if="expired(record)" color="red" class="expired">已过期</a-tag>
          </template>
        </a-table-column>
        <a-table-column key="parseResult" title="解析结果">
          <template #default="{ record }">
            <span class="clip">{{ record.parseResult || '—' }}</span>
          </template>
        </a-table-column>
        <a-table-column key="createdAt" title="入库时间" :width="160">
          <template #default="{ record }">
            {{ record.createdAt ? String(record.createdAt).replace('T', ' ').slice(0, 16) : '—' }}
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="暂无简历" hint="候选人上传简历后在此按保留期统一管理" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.resume-page { display: flex; flex-direction: column; gap: var(--space-6); }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); }
.head-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--color-text-primary); }
.head-sub { margin: var(--space-2) 0 0; font-size: 14px; color: var(--color-text-secondary); }
.table-card { border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.expired { margin-left: 8px; }
.clip { display: inline-block; max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
