<script setup lang="ts">
/**
 * 报表订阅新建/编辑弹窗：渠道多选 + 收件人 tags + CRON + 启停状态。
 * 启停语义由后端决定（状态 1 即注册调度任务），弹窗只负责提交完整 payload。
 */
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { errorText } from '../crud-helpers';
import {
  createReportSubscription,
  updateReportSubscription,
} from '../../../api/report-subscription';
import type { ReportSubscription, ReportSubscriptionRequest } from '../../../types/api';

const props = defineProps<{
  open: boolean;
  record: ReportSubscription | null;
  defOptions: { label: string; value: number }[];
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'saved'): void;
}>();

const channelOptions = [
  { label: '邮件', value: 'EMAIL' },
  { label: '企业微信', value: 'WECOM' },
  { label: '钉钉', value: 'DINGTALK' },
];

const submitting = ref(false);
const form = reactive<{
  reportDefId: number | undefined;
  cronExpr: string;
  channels: string[];
  recipients: string[];
  status: number;
}>({ reportDefId: undefined, cronExpr: '', channels: ['EMAIL'], recipients: [], status: 1 });

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    const r = props.record;
    form.reportDefId = r ? r.reportDefId : props.defOptions[0]?.value;
    form.cronExpr = r ? r.cronExpr : '0 0 8 * * *';
    form.channels = r ? [...(r.channels ?? [])] : ['EMAIL'];
    form.recipients = r ? [...(r.recipients ?? [])] : [];
    form.status = r ? r.status : 1;
  },
  { immediate: true },
);

function payload(): ReportSubscriptionRequest {
  return {
    reportDefId: Number(form.reportDefId),
    cronExpr: form.cronExpr.trim(),
    channels: form.channels,
    recipients: form.recipients,
    status: form.status,
  };
}

async function submit() {
  if (!form.reportDefId) {
    message.warning('请选择报表定义');
    return;
  }
  if (!form.cronExpr.trim()) {
    message.warning('请填写 CRON 表达式');
    return;
  }
  if (form.channels.length === 0) {
    message.warning('请至少选择一个推送渠道');
    return;
  }
  if (form.recipients.length === 0) {
    message.warning('请至少填写一个收件人');
    return;
  }
  submitting.value = true;
  try {
    if (props.record) {
      await updateReportSubscription(props.record.id, payload());
      message.success('订阅已更新');
    } else {
      await createReportSubscription(payload());
      message.success('订阅已创建，启用状态下将自动注册调度任务');
    }
    emit('update:open', false);
    emit('saved');
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="record ? '编辑报表订阅' : '新建报表订阅'"
    :confirm-loading="submitting"
    :mask-closable="false"
    ok-text="保存"
    cancel-text="取消"
    @update:open="emit('update:open', $event)"
    @ok="submit"
  >
    <a-form :model="form" layout="vertical">
      <a-form-item label="报表定义" required>
        <a-select
          v-model:value="form.reportDefId"
          :options="defOptions"
          placeholder="选择要订阅的报表"
          show-search
          option-filter-prop="label"
        />
      </a-form-item>
      <a-form-item label="CRON 表达式" required>
        <a-input v-model:value="form.cronExpr" placeholder="Spring 6 字段格式，如 0 0 8 * * *" />
      </a-form-item>
      <a-form-item label="推送渠道" required>
        <a-select v-model:value="form.channels" mode="multiple" :options="channelOptions" placeholder="可多选" />
      </a-form-item>
      <a-form-item label="收件人" required>
        <a-select v-model:value="form.recipients" mode="tags" placeholder="输入邮箱或用户 ID 后回车" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select
          v-model:value="form.status"
          :options="[
            { label: '启用（注册调度）', value: 1 },
            { label: '停用', value: 0 },
          ]"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
