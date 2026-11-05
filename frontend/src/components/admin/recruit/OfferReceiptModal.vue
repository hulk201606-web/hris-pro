<script setup lang="ts">
/**
 * Offer 回执登记（RCS-F-018）：ACCEPTED→ACCEPTED / DECLINED→REJECTED，
 * 重复回执幂等覆盖并刷新回执时间留痕。
 */
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { submitOfferReceipt } from '../../../api/recruit';
import { errorText } from '../crud-helpers';
import type { CrudRow } from '../../../types/admin';

const props = defineProps<{ open: boolean; offer: CrudRow | null }>();
const emit = defineEmits<{ 'update:open': [boolean]; done: [] }>();

const type = ref<'ACCEPTED' | 'DECLINED'>('ACCEPTED');
const note = ref('');
const submitting = ref(false);

async function handleOk() {
  if (!props.offer) return;
  submitting.value = true;
  try {
    const updated = await submitOfferReceipt(Number(props.offer.id), type.value, note.value.trim() || undefined);
    message.success(`回执已登记，Offer 状态：${updated.status}`);
    emit('update:open', false);
    emit('done');
  } catch (e) {
    message.error(errorText(e, '回执登记失败'));
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      type.value = 'ACCEPTED';
      note.value = '';
    }
  },
);
</script>

<template>
  <a-modal
    :open="open"
    title="回执登记"
    :confirm-loading="submitting"
    ok-text="登记"
    cancel-text="取消"
    @update:open="emit('update:open', $event)"
    @ok="handleOk"
  >
    <a-descriptions v-if="offer" :column="1" size="small" bordered class="brief">
      <a-descriptions-item label="Offer 编号">{{ offer.offerNo }}</a-descriptions-item>
      <a-descriptions-item label="当前状态">{{ offer.status }}</a-descriptions-item>
      <a-descriptions-item v-if="offer.receiptType" label="历史回执">
        {{ offer.receiptType }}{{ offer.receiptNote ? ` / ${offer.receiptNote}` : '' }}
      </a-descriptions-item>
    </a-descriptions>

    <a-form layout="vertical">
      <a-form-item label="回执结果" required>
        <a-radio-group v-model:value="type">
          <a-radio value="ACCEPTED">接受</a-radio>
          <a-radio value="DECLINED">拒绝</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="note" :rows="3" placeholder="选填，如拒绝原因" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.brief {
  margin-bottom: 12px;
}
</style>
