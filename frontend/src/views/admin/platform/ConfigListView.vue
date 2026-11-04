<script setup lang="ts">
/**
 * 管理端-系统配置：租户级参数（时区 / 日历 / 工号规则 / 邮件域名白名单等）。
 *
 * 接口形态：配置以 **configKey 字符串** 定位，PUT /plt/configs/{key} 是 upsert
 * （key 不存在则新建）。因此新增与编辑共用同一提交逻辑，仅 key 是否可编辑不同
 * ——编辑时 key 已作为路径参数，界面上锁死不可改。
 */
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { deleteConfig, listConfigs, saveConfig } from '../../../api/platform';
import type { ConfigResponse } from '../../../types/api';

const rows = ref<ConfigResponse[]>([]);
const loading = ref(false);
const submitting = ref(false);
const modalOpen = ref(false);
const editing = ref(false);

const form = reactive({ configKey: '', configValue: '' });

async function load() {
  loading.value = true;
  try {
    rows.value = await listConfigs();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '配置加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = false;
  form.configKey = '';
  form.configValue = '';
  modalOpen.value = true;
}

function openEdit(row: ConfigResponse) {
  editing.value = true;
  form.configKey = row.configKey;
  form.configValue = row.configValue;
  modalOpen.value = true;
}

async function handleSubmit() {
  if (!form.configKey.trim()) return message.warning('请填写配置键');
  submitting.value = true;
  try {
    await saveConfig(form.configKey.trim(), form.configValue);
    message.success('已保存');
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: ConfigResponse) {
  try {
    await deleteConfig(row.configKey);
    message.success('已删除');
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败');
  }
}

onMounted(load);
</script>

<template>
  <div class="config">
    <section class="head">
      <h2 class="head-title">系统配置</h2>
      <p class="head-sub">
        租户级参数，按配置键唯一。保存为 upsert：键不存在则新建，存在则覆盖值。
      </p>
    </section>

    <a-space class="toolbar">
      <a-button type="primary" @click="openCreate">新增配置</a-button>
      <a-button :loading="loading" @click="load">刷新</a-button>
    </a-space>

    <a-table
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="middle"
      :locale="{ emptyText: '暂无配置项' }"
    >
      <a-table-column key="configKey" title="配置键" data-index="configKey" :width="260" />
      <a-table-column key="configValue" title="配置值" data-index="configValue" />
      <a-table-column key="updatedAt" title="更新时间" :width="180">
        <template #default="{ record }">
          {{ record.updatedAt ? String(record.updatedAt).replace('T', ' ').slice(0, 19) : '-' }}
        </template>
      </a-table-column>
      <a-table-column key="actions" title="操作" :width="140">
        <template #default="{ record }">
          <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
          <a-popconfirm title="确认删除该配置？" @confirm="handleDelete(record)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </a-table-column>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑配置' : '新增配置'"
      :confirm-loading="submitting"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="配置键" required>
          <a-input
            v-model:value="form.configKey"
            :disabled="editing"
            placeholder="如 tenant.timezone"
          />
          <div v-if="editing" class="tip">配置键为定位标识，编辑时不可修改</div>
        </a-form-item>
        <a-form-item label="配置值">
          <a-textarea v-model:value="form.configValue" :rows="3" placeholder="配置值" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.config {
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
.tip {
  margin-top: 4px;
  color: var(--color-text-secondary, #999);
  font-size: 12px;
}
</style>
