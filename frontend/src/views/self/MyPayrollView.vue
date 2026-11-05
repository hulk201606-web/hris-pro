<script setup lang="ts">
/**
 * 我的薪酬（自助）：掩码工资条列表 + 可选明文查看。
 * 列表调 GET /cmp/payslips/me（后端按当前员工过滤，仅掩码字段）；
 * 明文调 GET /cmp/salary-secret/payslips/{id}，需 cmp:payslip:read + cmp:salary:read 双权限，
 * 员工角色通常不持有密域权限码，此时隐藏明文按钮并展示说明。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { EyeOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../components/admin/EmptyHint.vue';
import { fetchMyPayslips, fetchPayslipSecret } from '../../api/self';
import type { MyPayslip } from '../../api/self';
import { useUserStore } from '../../stores/user';

const STATUS_META: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待发放', color: 'orange' },
  SENT: { text: '已发放', color: 'blue' },
  VIEWED: { text: '已查看', color: 'green' },
};

const userStore = useUserStore();
const payslips = ref<MyPayslip[]>([]);
const loading = ref(true);
const errorText = ref('');

const secretModalOpen = ref(false);
const secretLoading = ref(false);
const secretValue = ref('');
const secretPeriod = ref('');

/** 密域明文按钮仅对同时持有实体读权限与密域读权限的用户可见 */
const canViewSecret = computed(
  () => userStore.hasPerm('cmp:payslip:read') && userStore.hasPerm('cmp:salary:read'),
);

const columns = [
  { title: '薪酬期间', dataIndex: 'periodCode', key: 'periodCode' },
  { title: '应发（掩码）', dataIndex: 'grossMask', key: 'grossMask', align: 'right' as const },
  { title: '实发（掩码）', dataIndex: 'netPayMask', key: 'netPayMask', align: 'right' as const },
  { title: '状态', key: 'sendStatus' },
  { title: '发放时间', dataIndex: 'sentAt', key: 'sentAt' },
  { title: '操作', key: 'action', width: 110 },
];

function fmtTime(v: string | null): string {
  return v ? v.replace('T', ' ').slice(0, 19) : '-';
}

function statusMeta(status: string) {
  return STATUS_META[status] ?? { text: status || '-', color: 'default' };
}

async function openSecret(row: MyPayslip) {
  secretPeriod.value = row.periodCode || `工资单 #${row.id}`;
  secretModalOpen.value = true;
  secretLoading.value = true;
  secretValue.value = '';
  try {
    const secret = await fetchPayslipSecret(row.id);
    secretValue.value = secret.netPayCipher || secret.netPayMask || '-';
  } catch (e) {
    secretModalOpen.value = false;
    message.error(e instanceof Error ? e.message : '明文读取失败');
  } finally {
    secretLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  errorText.value = '';
  try {
    payslips.value = (await fetchMyPayslips()) ?? [];
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : '工资条加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="my-payroll">
    <section class="head">
      <h2 class="head-title">我的薪酬</h2>
      <p class="head-sub">工资条按月发放，默认展示掩码金额</p>
    </section>

    <a-alert v-if="errorText" type="error" :message="errorText" show-icon class="error" />

    <a-card :bordered="false" class="block-card">
      <a-table
        :columns="columns"
        :data-source="payslips"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: false }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'periodCode'">
            {{ (record as MyPayslip).periodCode || '-' }}
          </template>
          <template v-else-if="column.key === 'grossMask'">
            {{ (record as MyPayslip).grossMask || '-' }}
          </template>
          <template v-else-if="column.key === 'netPayMask'">
            {{ (record as MyPayslip).netPayMask || '-' }}
          </template>
          <template v-else-if="column.key === 'sendStatus'">
            <a-tag :color="statusMeta((record as MyPayslip).sendStatus).color">
              {{ statusMeta((record as MyPayslip).sendStatus).text }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'sentAt'">
            {{ fmtTime((record as MyPayslip).sentAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-if="canViewSecret"
              type="link"
              size="small"
              @click="openSecret(record as MyPayslip)"
            >
              <EyeOutlined /> 查看明文
            </a-button>
            <span v-else class="no-secret">-</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无工资条记录" hint="薪酬核算发放后即可在此查看" />
        </template>
      </a-table>
      <p class="secret-note">
        <template v-if="canViewSecret">明文实发金额仅限本人查看，读取行为将记入审计日志。</template>
        <template v-else>
          工资条明文需薪酬密域权限，当前账号仅展示掩码金额；如有疑问请联系人事。
        </template>
      </p>
    </a-card>

    <a-modal
      v-model:open="secretModalOpen"
      :title="`实发明文 · ${secretPeriod}`"
      :footer="null"
      :width="360"
    >
      <a-spin :spinning="secretLoading">
        <p class="secret-value">{{ secretValue || '加载中' }}</p>
      </a-spin>
      <p class="secret-tip">该金额为薪酬密域明文，请勿截图外传。</p>
    </a-modal>
  </div>
</template>

<style scoped>
.my-payroll {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
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

.block-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.error {
  border-radius: var(--radius-md);
}

.no-secret {
  color: var(--color-text-muted);
}

.secret-note {
  margin: var(--space-3) 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.secret-value {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.secret-tip {
  margin: var(--space-3) 0 0;
  font-size: 12px;
  text-align: center;
  color: var(--color-text-muted);
}
</style>
