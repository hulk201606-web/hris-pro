<script setup lang="ts">

/**
 * 组织版本（批 8 #16 MVP / 批 11 #23 状态机）。
 * 后端 OrgVersionController：列表 GET /org/versions?status=（缺省 ACTIVE；DRAFT/ALL 需写权限），
 * 新建 POST /org/versions（body.effectiveFrom 可空，状态固定 DRAFT），
 * 激活 POST /org/versions/{no}/activate，回滚 POST /org/versions/{no}/rollback（草稿不可回滚）。
 * 权限复用 org:dept:read / org:dept:write。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  activateOrgVersion,
  createOrgVersion,
  listOrgVersions,
  rollbackOrgVersion,
} from '../../../api/org-version';
import { useUserStore } from '../../../stores/user';
import type { OrgVersion } from '../../../types/api';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('org:dept:write'));

const rows = ref<OrgVersion[]>([]);
const loading = ref(false);
const statusFilter = ref<string>('ACTIVE');

/** 含草稿的过滤项只对有写权限的管理端开放 */
const statusOptions = computed(() => {
  const base = [
    { label: '生效版本（ACTIVE）', value: 'ACTIVE' },
    { label: '已替代（SUPERSEDED）', value: 'SUPERSEDED' },
  ];
  if (!canWrite.value) return base;
  return [...base, { label: '草稿（DRAFT）', value: 'DRAFT' }, { label: '全部状态（ALL）', value: 'ALL' }];
});

const createOpen = ref(false);
const createSubmitting = ref(false);
const createForm = reactive<{ effectiveFrom: string | null }>({ effectiveFrom: null });

const actingNo = ref<string | null>(null);

const STATUS_META: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  ACTIVE: { text: '生效中', color: 'green' },
  SUPERSEDED: { text: '已替代', color: 'orange' },
};

function statusText(status: string): string {
  return STATUS_META[status]?.text ?? status;
}

function statusColor(status: string): string {
  return STATUS_META[status]?.color ?? 'default';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listOrgVersions(statusFilter.value);
  } catch (e) {
    message.error(errorText(e, '组织版本加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  createForm.effectiveFrom = null;
  createOpen.value = true;
}

async function submitCreate() {
  createSubmitting.value = true;
  try {
    const created = await createOrgVersion(createForm.effectiveFrom ?? undefined);
    message.success(`版本 ${created.versionNo} 已创建（草稿），生效日 ${created.effectiveFrom || '今天'}`);
    createOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '创建版本失败'));
  } finally {
    createSubmitting.value = false;
  }
}

async function activate(record: OrgVersion) {
  actingNo.value = record.versionNo;
  try {
    await activateOrgVersion(record.versionNo);
    message.success(`版本 ${record.versionNo} 已激活，原生效版本转为已替代`);
    await load();
  } catch (e) {
    message.error(errorText(e, '激活失败'));
  } finally {
    actingNo.value = null;
  }
}

async function rollback(record: OrgVersion) {
  actingNo.value = record.versionNo;
  try {
    await rollbackOrgVersion(record.versionNo);
    message.success(`已回滚至版本 ${record.versionNo}`);
    await load();
  } catch (e) {
    message.error(errorText(e, '回滚失败'));
  } finally {
    actingNo.value = null;
  }
}

onMounted(load);
</script>

<template>
  <div class="ver-page">
    <section class="head">
      <div>
        <h2 class="head-title">组织版本</h2>
        <p class="head-sub">版本为岗位与职级的全量快照；到期可由调度自动激活，也可手动激活或回滚</p>
      </div>
      <a-space>
        <a-select v-model:value="statusFilter" :options="statusOptions" style="width: 190px" @change="load" />
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建版本</a-button>
      </a-space>
    </section>

    <a-alert
      type="info"
      show-icon
      message="到期自动激活需在调度中心注册日任务"
      description="平台调度中心 → 新建任务：taskKey 建议 ORG_VERSION_ACTIVATE_DUE，cron 每日一次，处理器指向组织版本到期激活（DRAFT 且生效日≤今天逐个激活，幂等）。"
    />

    <a-card :bordered="false" class="card">
      <a-table
        row-key="versionNo"
        :columns="[
          { title: '版本号', dataIndex: 'versionNo', key: 'versionNo', width: 200 },
          { title: '快照日期', dataIndex: 'snapshotDate', key: 'snapshotDate', width: 130 },
          { title: '状态', key: 'status', width: 110 },
          { title: '生效日', dataIndex: 'effectiveFrom', key: 'effectiveFrom', width: 130 },
          { title: '岗位数', dataIndex: 'positionCount', key: 'positionCount', width: 100 },
          { title: '职级数', dataIndex: 'gradeCount', key: 'gradeCount', width: 100 },
          { title: '操作', key: 'action', width: 160, fixed: 'right' },
        ]"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 960 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'effectiveFrom'">{{ record.effectiveFrom || '立即' }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canWrite">
              <a-popconfirm
                v-if="record.status === 'DRAFT'"
                title="激活后该版本立即生效，原生效版本转为已替代，确认？"
                @confirm="activate(record)"
              >
                <a-button type="link" size="small" :loading="actingNo === record.versionNo">激活</a-button>
              </a-popconfirm>
              <a-popconfirm
                v-else-if="record.status === 'SUPERSEDED'"
                title="回滚将按该快照恢复岗位与职级并激活此版本，确认？"
                @confirm="rollback(record)"
              >
                <a-button type="link" size="small" :loading="actingNo === record.versionNo">回滚</a-button>
              </a-popconfirm>
              <span v-else class="muted">当前生效</span>
            </a-space>
            <span v-else class="muted">—</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint
            description="暂无组织版本"
            :hint="canWrite ? '点击右上角「新建版本」生成岗位与职级快照' : '仅管理端可查看草稿版本'"
          />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="createOpen"
      title="新建组织版本"
      :confirm-loading="createSubmitting"
      :mask-closable="false"
      ok-text="创建草稿"
      cancel-text="取消"
      @ok="submitCreate"
    >
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="计划生效日">
          <a-date-picker
            v-model:value="createForm.effectiveFrom"
            value-format="YYYY-MM-DD"
            placeholder="留空表示立即（按当天落库）"
            style="width: 100%"
          />
        </a-form-item>
        <p class="tip">新版本状态固定为草稿，生效日到期后可由调度任务自动激活，也可在列表中手动激活。</p>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.ver-page {
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

.card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.tip {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.muted {
  color: var(--color-text-muted);
}
</style>
