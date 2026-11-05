<script setup lang="ts">
/**
 * Offer 发送弹窗（RCS-F-018）：渠道仅 EMAIL/WECOM/DINGTALK，recipient 缺省由 PLT 按模板收件人投递。
 * 发送结果 status=SENT/FAILED；FAILED 时 error 透出原因，Offer 保持 DRAFT（fail-closed）。
 */
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { sendOffer } from '../../../api/recruit';
import { errorText } from '../crud-helpers';
import type { OfferSendResult } from '../../../types/api-batch9';
import type { CrudRow } from '../../../types/admin';

const props = defineProps<{ open: boolean; offer: CrudRow | null }>();
const emit = defineEmits<{ 'update:open': [boolean]; done: [] }>();

const channel = ref<'EMAIL' | 'WECOM' | 'DINGTALK'>('EMAIL');
const recipient = ref('');
const sending = ref(false);
const result = ref<OfferSendResult | null>(null);

async function handleSend() {
  if (!props.offer) return;
  sending.value = true;
  try {
    result.value = await sendOffer(Number(props.offer.id), channel.value, recipient.value.trim() || undefined);
    if (result.value.status === 'SENT') {
      message.success('Offer 已发送');
      emit('done');
    } else {
      message.error(result.value.error || '发送失败，Offer 保持草稿');
    }
  } catch (e) {
    message.error(errorText(e, '发送失败'));
  } finally {
    sending.value = false;
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      channel.value = 'EMAIL';
      recipient.value = '';
      result.value = null;
    }
  },
);
</script>

<template>
  <a-modal
    :open="open"
    title="发送 Offer"
    :confirm-loading="sending"
    ok-text="发送"
    cancel-text="关闭"
    @update:open="emit('update:open', $event)"
    @ok="handleSend"
  >
    <a-descriptions v-if="offer" :column="1" size="small" bordered class="brief">
      <a-descriptions-item label="Offer 编号">{{ offer.offerNo }}</a-descriptions-item>
      <a-descriptions-item label="当前状态">{{ offer.status }}</a-descriptions-item>
    </a-descriptions>

    <a-form layout="vertical">
      <a-form-item label="发送渠道" required>
        <a-select v-model:value="channel">
          <a-select-option value="EMAIL">邮件</a-select-option>
          <a-select-option value="WECOM">企业微信</a-select-option>
          <a-select-option value="DINGTALK">钉钉</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="收件人">
        <a-input v-model:value="recipient" placeholder="选填，缺省按模板收件人投递" />
      </a-form-item>
    </a-form>

    <a-alert
      v-if="result"
      :type="result.status === 'SENT' ? 'success' : 'error'"
      show-icon
      :message="`发送结果：${result.status}`"
      :description="result.status === 'FAILED' ? result.error || '未知原因' : `Offer 状态已更新为 ${result.offerStatus}`"
    />
  </a-modal>
</template>

<style scoped>
.brief {
  margin-bottom: 12px;
}
</style>
