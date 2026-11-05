<script setup lang="ts">

/**
 * 考勤围栏管理（批 12#27）：name / lat / lng / radiusM / enabled 的增删改查。
 * 后端 LocationController：读 att:group:read，写 att:group:write；radiusM 缺省 200 米。
 * 围栏被考勤组绑定后生效：打卡坐标落在半径外即拒绝，requireLocation=true 时无坐标也拒绝。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  createLocation,
  deleteLocation,
  listLocations,
  updateLocation,
} from '../../../api/attendance-fence';
import { useUserStore } from '../../../stores/user';
import type { AttLocation } from '../../../types/api';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('att:group:write'));

const rows = ref<AttLocation[]>([]);
const loading = ref(false);
/** 列表过滤：ALL=全部，true/false 对应 enabled 查询参数 */
const enabledFilter = ref<'ALL' | 'true' | 'false'>('ALL');

const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const form = reactive<{
  name: string;
  lat: number | null;
  lng: number | null;
  radiusM: number | null;
  enabled: boolean;
}>({ name: '', lat: null, lng: null, radiusM: 200, enabled: true });

async function load() {
  loading.value = true;
  try {
    const enabled = enabledFilter.value === 'ALL' ? undefined : enabledFilter.value === 'true';
    rows.value = await listLocations(enabled);
  } catch (e) {
    message.error(errorText(e, '围栏列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.name = '';
  form.lat = null;
  form.lng = null;
  form.radiusM = 200;
  form.enabled = true;
}

function openCreate() {
  resetForm();
  editingId.value = null;
  modalOpen.value = true;
}

function openEdit(record: AttLocation) {
  form.name = record.name;
  form.lat = Number(record.lat);
  form.lng = Number(record.lng);
  form.radiusM = record.radiusM ?? 200;
  form.enabled = record.enabled !== false;
  editingId.value = record.id;
  modalOpen.value = true;
}

async function submit() {
  if (!form.name.trim()) {
    message.warning('围栏名称不能为空');
    return;
  }
  if (form.lat === null || form.lng === null) {
    message.warning('请填写纬度与经度');
    return;
  }
  const payload = {
    name: form.name.trim(),
    lat: form.lat,
    lng: form.lng,
    radiusM: form.radiusM ?? 200,
    enabled: form.enabled,
  };
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await createLocation(payload);
      message.success('围栏已创建');
    } else {
      await updateLocation(editingId.value, payload);
      message.success('围栏已更新');
    }
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

function confirmRemove(record: AttLocation) {
  Modal.confirm({
    title: `删除围栏「${record.name}」？`,
    content: '删除后绑定该围栏的考勤组将失去地理校验，请先解绑。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    async onOk() {
      try {
        await deleteLocation(record.id);
        message.success('已删除');
        await load();
      } catch (e) {
        message.error(errorText(e, '删除失败'));
      }
    },
  });
}

function enabledText(v: boolean | null | undefined): string {
  return v === false ? '停用' : '启用';
}

onMounted(load);
</script>

<template>
  <div class="loc-page">
    <section class="head">
      <div>
        <h2 class="head-title">考勤围栏</h2>
        <p class="head-sub">按经纬度与半径划定打卡范围，考勤组绑定后按围栏校验</p>
      </div>
      <a-space>
        <a-select
          v-model:value="enabledFilter"
          :options="[
            { label: '全部状态', value: 'ALL' },
            { label: '启用', value: 'true' },
            { label: '停用', value: 'false' },
          ]"
          style="width: 130px"
          @change="load"
        />
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建围栏</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="card">
      <a-table
        row-key="id"
        :columns="[
          { title: '围栏名称', dataIndex: 'name', key: 'name', width: 180 },
          { title: '纬度', dataIndex: 'lat', key: 'lat', width: 130 },
          { title: '经度', dataIndex: 'lng', key: 'lng', width: 130 },
          { title: '半径（米）', dataIndex: 'radiusM', key: 'radiusM', width: 120 },
          { title: '状态', key: 'enabled', width: 100 },
          { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
          { title: '操作', key: 'action', width: 140, fixed: 'right' },
        ]"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 980 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled === false ? 'default' : 'green'">{{ enabledText(record.enabled) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'radiusM'">{{ record.radiusM ?? 200 }}</template>
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
          <EmptyHint description="暂无围栏" :hint="canWrite ? '点击右上角「新建围栏」添加打卡范围' : ''" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId === null ? '新建围栏' : '编辑围栏'"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="围栏名称" required>
          <a-input v-model:value="form.name" placeholder="如 总部大楼" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="纬度" required>
              <a-input-number v-model:value="form.lat" :min="-90" :max="90" :step="0.000001" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="经度" required>
              <a-input-number v-model:value="form.lng" :min="-180" :max="180" :step="0.000001" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="半径（米）">
              <a-input-number v-model:value="form.radiusM" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="启用">
              <a-switch v-model:checked="form.enabled" />
            </a-form-item>
          </a-col>
        </a-row>
        <p class="tip">半径缺省 200 米；纬度范围 -90~90，经度范围 -180~180。</p>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.loc-page {
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
