<script setup lang="ts">

/**
 * 考勤机设备管理（批 12#27）：deviceSn / locationId / status 的增删改查。
 * 后端 DeviceController：读 att:group:read，写 att:group:write；status 取 ACTIVE/DISABLED。
 * 设备密钥（deviceKey）仅新增时录入，后端只存摘要且任何响应都不回显；
 * 推送打卡走 POST /internal/att/machine-punch，前端不参与。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  createDevice,
  deleteDevice,
  listDevices,
  listLocations,
  updateDevice,
} from '../../../api/attendance-fence';
import { useUserStore } from '../../../stores/user';
import type { AttDevice, AttLocation } from '../../../types/api';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('att:group:write'));

const rows = ref<AttDevice[]>([]);
const locations = ref<AttLocation[]>([]);
const loading = ref(false);
const statusFilter = ref<string>('ALL');

const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const form = reactive<{
  deviceSn: string;
  deviceKey: string;
  locationId: number | undefined;
  status: string;
}>({ deviceSn: '', deviceKey: '', locationId: undefined, status: 'ACTIVE' });

const locationOptions = computed(() =>
  locations.value.map((l) => ({ label: l.name, value: l.id })),
);
const locationName = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {};
  locations.value.forEach((l) => {
    map[l.id] = l.name;
  });
  return map;
});

async function load() {
  loading.value = true;
  try {
    rows.value = await listDevices(statusFilter.value === 'ALL' ? undefined : statusFilter.value);
  } catch (e) {
    message.error(errorText(e, '设备列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadLocations() {
  try {
    locations.value = await listLocations();
  } catch {
    locations.value = [];
  }
}

function openCreate() {
  form.deviceSn = '';
  form.deviceKey = '';
  form.locationId = undefined;
  form.status = 'ACTIVE';
  editingId.value = null;
  modalOpen.value = true;
}

function openEdit(record: AttDevice) {
  form.deviceSn = record.deviceSn;
  form.deviceKey = '';
  form.locationId = record.locationId ?? undefined;
  form.status = record.status ?? 'ACTIVE';
  editingId.value = record.id;
  modalOpen.value = true;
}

async function submit() {
  if (!form.deviceSn.trim()) {
    message.warning('设备序列号不能为空');
    return;
  }
  if (editingId.value === null && !form.deviceKey.trim()) {
    message.warning('新增设备必须录入设备密钥');
    return;
  }
  const payload: Record<string, unknown> = {
    deviceSn: form.deviceSn.trim(),
    locationId: form.locationId ?? null,
    status: form.status,
  };
  if (form.deviceKey.trim()) payload.deviceKey = form.deviceKey.trim();
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await createDevice(payload);
      message.success('设备已注册');
    } else {
      await updateDevice(editingId.value, payload);
      message.success('设备已更新');
    }
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

function confirmRemove(record: AttDevice) {
  Modal.confirm({
    title: `删除设备「${record.deviceSn}」？`,
    content: '删除后该设备推送的打卡将被拒绝，历史打卡记录不受影响。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    async onOk() {
      try {
        await deleteDevice(record.id);
        message.success('已删除');
        await load();
      } catch (e) {
        message.error(errorText(e, '删除失败'));
      }
    },
  });
}

onMounted(() => {
  load();
  loadLocations();
});
</script>

<template>
  <div class="dev-page">
    <section class="head">
      <div>
        <h2 class="head-title">考勤机设备</h2>
        <p class="head-sub">设备密钥仅在注册时录入且不回显；停用设备将拒绝推送打卡</p>
      </div>
      <a-space>
        <a-select
          v-model:value="statusFilter"
          :options="[
            { label: '全部状态', value: 'ALL' },
            { label: '启用', value: 'ACTIVE' },
            { label: '停用', value: 'DISABLED' },
          ]"
          style="width: 130px"
          @change="load"
        />
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">注册设备</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="card">
      <a-table
        row-key="id"
        :columns="[
          { title: '设备序列号', dataIndex: 'deviceSn', key: 'deviceSn', width: 200 },
          { title: '绑定围栏', key: 'locationId', width: 200 },
          { title: '状态', key: 'status', width: 110 },
          { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
          { title: '操作', key: 'action', width: 140, fixed: 'right' },
        ]"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 860 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'locationId'">
            {{ record.locationId ? locationName[record.locationId] ?? `围栏 #${record.locationId}` : '未绑定' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">
              {{ record.status === 'ACTIVE' ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ (record.updatedAt || '').replace('T', ' ').slice(0, 19) || '—' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canWrite">
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button type="link" size="small" danger @click="confirmRemove(record)">删除</a-button>
            </a-space>
            <span v-else class="muted">—</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无考勤机" :hint="canWrite ? '点击右上角「注册设备」录入序列号与密钥' : ''" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId === null ? '注册设备' : '编辑设备'"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="设备序列号" required>
          <a-input v-model:value="form.deviceSn" placeholder="如 ATT-SN-0001" />
        </a-form-item>
        <a-form-item label="设备密钥" :required="editingId === null">
          <a-input-password
            v-model:value="form.deviceKey"
            :placeholder="editingId === null ? '注册时必填，服务端仅存摘要' : '留空表示不轮换密钥'"
          />
        </a-form-item>
        <a-form-item label="绑定围栏">
          <a-select
            v-model:value="form.locationId"
            :options="locationOptions"
            allow-clear
            placeholder="不绑定表示不做地理校验"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select
            v-model:value="form.status"
            :options="[
              { label: '启用', value: 'ACTIVE' },
              { label: '停用', value: 'DISABLED' },
            ]"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.dev-page {
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

.muted {
  color: var(--color-text-muted);
}
</style>
