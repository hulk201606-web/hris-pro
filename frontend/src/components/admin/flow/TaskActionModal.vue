<script setup lang="ts">
/**
 * 流程任务操作弹窗：同意 / 驳回 / 转办 / 加签 / 撤回 五种动作共用。
 *
 * 各动作的参数差异只有「是否需要目标用户」：
 *   approve / reject / withdraw 只需意见
 *   transfer / addSign         还需目标用户 ID
 * 因此用一个弹窗按 action 动态渲染，避免五份几乎相同的对话框。
 */
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { addSignTask, approveTask, rejectTask, transferTask, withdrawTask } from '../../../api/flow';
import type { TaskView } from '../../../types/api';
import DynamicForm from '../DynamicForm.vue';

export type TaskAction = 'approve' | 'reject' | 'transfer' | 'addSign' | 'withdraw';

const props = defineProps<{
  open: boolean;
  action: TaskAction | null;
  task: TaskView | null;
}>();

const emit = defineEmits<{ 'update:open': [boolean]; done: [] }>();

const comment = ref('');
const targetUserId = ref('');
const submitting = ref(false);
const formValues = ref<Record<string, unknown>>({});
/** 已解析的动态表单 Schema；为空表示该任务无动态表单或解析失败（保持现状仅意见输入） */
const parsedSchema = ref<Record<string, unknown> | null>(null);

const ACTION_META: Record<TaskAction, { title: string; needTarget: boolean; okText: string }> = {
  approve: { title: '同意', needTarget: false, okText: '确认同意' },
  reject: { title: '驳回', needTarget: false, okText: '确认驳回' },
  transfer: { title: '转办', needTarget: true, okText: '确认转办' },
  addSign: { title: '加签', needTarget: true, okText: '确认加签' },
  withdraw: { title: '撤回', needTarget: false, okText: '确认撤回' },
};

const meta = computed(() => (props.action ? ACTION_META[props.action] : null));

watch(
  () => props.open,
  (v) => {
    if (v) {
      comment.value = '';
      targetUserId.value = '';
      formValues.value = {};
      parsedSchema.value = null;
      const raw = props.task?.formSchemaJson;
      if (props.action === 'approve' && raw) {
        try {
          const parsed: unknown = JSON.parse(raw);
          if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
            parsedSchema.value = parsed as Record<string, unknown>;
          }
        } catch {
          // Schema 非法时降级为仅意见输入，不阻断审批
        }
      }
    }
  },
);

async function handleOk() {
  const task = props.task;
  if (!props.action || !task) return;
  if (meta.value?.needTarget && !targetUserId.value.trim()) {
    return message.warning('请填写目标用户 ID');
  }
  const id = task.taskId;
  // 契约中 taskId 允许为 null（引擎偶发返回缺失任务 ID），此时无法定位任务
  if (!id) return message.error('该任务缺少任务 ID，无法操作，请刷新后重试');
  submitting.value = true;
  try {
    switch (props.action) {
      case 'approve': {
        const hasValues = parsedSchema.value && Object.keys(formValues.value).length > 0;
        const req = {
          comment: comment.value,
          ...(hasValues ? { formValues: formValues.value } : {}),
        };
        await approveTask(id, req as never);
        break;
      }
      case 'reject':
        await rejectTask(id, { comment: comment.value } as never);
        break;
      case 'transfer':
        await transferTask(id, targetUserId.value.trim(), comment.value);
        break;
      case 'addSign':
        await addSignTask(id, targetUserId.value.trim(), comment.value);
        break;
      case 'withdraw':
        await withdrawTask(id, comment.value);
        break;
    }
    message.success(`${meta.value?.title}成功`);
    emit('update:open', false);
    emit('done');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="meta ? `${meta.title}任务` : '任务操作'"
    :confirm-loading="submitting"
    :ok-text="meta?.okText"
    @update:open="emit('update:open', $event)"
    @ok="handleOk"
  >
    <a-descriptions v-if="task" :column="1" size="small" bordered class="brief">
      <a-descriptions-item label="节点">{{ task.nodeName }}</a-descriptions-item>
      <a-descriptions-item label="业务类型">{{ task.bizType || '-' }}</a-descriptions-item>
      <a-descriptions-item label="业务键">{{ task.bizKey || '-' }}</a-descriptions-item>
    </a-descriptions>

    <DynamicForm
      v-if="parsedSchema"
      v-model="formValues"
      :schema="parsedSchema"
      class="dynamic"
    />

    <a-form layout="vertical" class="form">
      <a-form-item v-if="meta?.needTarget" label="目标用户 ID" required>
        <a-input v-model:value="targetUserId" placeholder="转办/加签的目标用户 ID" />
      </a-form-item>
      <a-form-item label="审批意见">
        <a-textarea v-model:value="comment" :rows="3" placeholder="选填" />
      </a-form-item>
    </a-form>

    <a-alert
      v-if="action === 'withdraw'"
      type="info"
      show-icon
      message="撤回仅在首节点未被处理时可用；若已被处理，服务端会拒绝。"
    />
  </a-modal>
</template>

<style scoped>
.brief {
  margin-bottom: 16px;
}
.dynamic {
  margin-top: 8px;
}
.form {
  margin-top: 8px;
}
</style>
