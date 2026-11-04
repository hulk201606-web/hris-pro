<script setup lang="ts">
/**
 * 管理端-候选人查重（RCS-F-009）：简历入库前检查是否已有同人，避免重复投递记录分裂。
 *
 * 两个匹配维度的可信度不同，界面上必须区分，否则会把同名当成同一人误合并：
 *   EMAIL（强）—— 同一邮箱基本可判定为同一人，建议直接复用现有候选人
 *   NAME（弱）—— 同名不必然是同一人，仅作提示，需人工核对手机号/经历后再决定
 *
 * 手机号未纳入查重：该列由调用方传入 mobileCipher 后原样落库，
 * 若上游用随机 IV 加密则同号密文不同，等值查询不可靠。
 */
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { checkCandidateDuplicate } from '../../../api/recruit';
import type { CandidateDedupResponse } from '../../../types/api';

const email = ref('');
const name = ref('');
const result = ref<CandidateDedupResponse | null>(null);
const loading = ref(false);

const emailHits = computed(
  () => result.value?.matches?.filter((m) => m.matchField === 'EMAIL').length ?? 0,
);
const nameOnlyHits = computed(
  () => result.value?.matches?.filter((m) => m.matchField === 'NAME').length ?? 0,
);

async function handleCheck() {
  const e = email.value.trim();
  const n = name.value.trim();
  if (!e && !n) return message.warning('邮箱与姓名至少填一个');
  loading.value = true;
  result.value = null;
  try {
    result.value = await checkCandidateDuplicate(e || undefined, n || undefined);
  } catch (err) {
    message.error(err instanceof Error ? err.message : '查重失败');
  } finally {
    loading.value = false;
  }
}

function reset() {
  email.value = '';
  name.value = '';
  result.value = null;
}
</script>

<template>
  <div class="dedup">
    <section class="head">
      <h2 class="head-title">候选人查重</h2>
      <p class="head-sub">
        简历入库前检查是否已有同人。邮箱命中可直接复用现有记录；姓名命中仅为提示，
        同名不必然同人，需核对手机号与经历后再决定。
      </p>
    </section>

    <a-card :bordered="false" class="query-card">
      <a-form layout="vertical" :model="{ email, name }">
        <a-row :gutter="16">
          <a-col :span="9">
            <a-form-item label="邮箱（强匹配）">
              <a-input v-model:value="email" placeholder="候选人邮箱" allow-clear @press-enter="handleCheck" />
            </a-form-item>
          </a-col>
          <a-col :span="9">
            <a-form-item label="姓名（弱匹配）">
              <a-input v-model:value="name" placeholder="候选人姓名" allow-clear @press-enter="handleCheck" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label=" ">
              <a-space>
                <a-button type="primary" :loading="loading" @click="handleCheck">查重</a-button>
                <a-button @click="reset">清空</a-button>
              </a-space>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <template v-if="result">
      <a-alert
        v-if="emailHits > 0"
        type="warning"
        show-icon
        class="result-alert"
        :message="`命中 ${emailHits} 条同邮箱记录`"
        description="同一邮箱基本可判定为同一人，建议直接复用现有候选人记录，不要重复创建。"
      />
      <a-alert
        v-else-if="nameOnlyHits > 0"
        type="info"
        show-icon
        class="result-alert"
        :message="`命中 ${nameOnlyHits} 条同名记录`"
        description="同名不必然是同一人。请核对手机号、工作经历等信息后，再决定是合并还是新建。"
      />
      <a-alert
        v-else
        type="success"
        show-icon
        class="result-alert"
        message="未发现重复候选人"
        description="该邮箱与姓名在候选人库中无命中，可正常创建。"
      />

      <a-table
        v-if="result.matches && result.matches.length"
        :data-source="result.matches"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <a-table-column key="name" title="姓名" data-index="name" :width="140" />
        <a-table-column key="email" title="邮箱" data-index="email" :width="220" />
        <a-table-column key="mobileMask" title="手机号（掩码）" :width="160">
          <template #default="{ record }">{{ record.mobileMask || '-' }}</template>
        </a-table-column>
        <a-table-column key="status" title="状态" :width="120">
          <template #default="{ record }">{{ record.status || '-' }}</template>
        </a-table-column>
        <a-table-column key="matchField" title="命中维度" :width="130">
          <template #default="{ record }">
            <a-tag :color="record.matchField === 'EMAIL' ? 'orange' : 'default'">
              {{ record.matchField === 'EMAIL' ? '邮箱（强）' : '姓名（弱）' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column key="id" title="候选人 ID" :width="200" />
      </a-table>
    </template>
  </div>
</template>

<style scoped>
.dedup {
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
.query-card {
  margin-bottom: 16px;
}
.result-alert {
  margin-bottom: 16px;
}
</style>
