<script setup lang="ts">
/**
 * 简历解析结果抽屉：展示 parsed 三字段 + 教育/工作经历 JSON 数组表格，
 * RULE_LIMITED 时给出黄色提示；支持重新解析与人工修正（ResumeParseFixModal）。
 * 走 /rcu/resumes/{id}/parse 契约（GET / POST 重跑 / PUT 人工修正）。
 * resumeId 为 rcu_resume.id；候选人行未携带该 ID 时由抽屉内输入定位。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../EmptyHint.vue';
import ResumeParseFixModal from './ResumeParseFixModal.vue';
import { errorText } from '../crud-helpers';
import { getResumeParse, parseResume } from '../../../api/recruit';
import type { ResumeParseResult } from '../../../types/api-batch2';

const props = defineProps<{
  /** 简历 ID（rcu_resume.id），可为空（由抽屉内输入定位） */
  resumeId?: number;
  /** 是否可写（重新解析 / 人工修正），对应 rcu:resume:write */
  canWrite: boolean;
}>();

const emit = defineEmits<{ (e: 'close'): void }>();

const activeId = ref<number | null>(props.resumeId ?? null);
const idInput = ref<number | undefined>(undefined);
const loading = ref(false);
const result = ref<ResumeParseResult | null>(null);
const fixOpen = ref(false);

const isRuleLimited = computed(() => result.value?.parseStatus === 'RULE_LIMITED');
const educationRows = computed(() => parseList(result.value?.educationJson).map((r, i) => ({ ...r, key: i })));
const experienceRows = computed(() => parseList(result.value?.experienceJson).map((r, i) => ({ ...r, key: i })));

const eduColumns = [
  { title: '学校', dataIndex: 'school', key: 'school' },
  { title: '专业', dataIndex: 'major', key: 'major' },
  { title: '学历', dataIndex: 'degree', key: 'degree', width: 90 },
  { title: '起止', key: 'span', width: 190 },
];
const expColumns = [
  { title: '公司', dataIndex: 'company', key: 'company' },
  { title: '职位', dataIndex: 'positionName', key: 'positionName' },
  { title: '起止', key: 'span', width: 190 },
];

/** 后端下发 JSON 字符串（educationJson/experienceJson），兜底兼容数组形态 */
function parseList(v: unknown): Record<string, unknown>[] {
  if (!v) return [];
  if (Array.isArray(v)) return v as Record<string, unknown>[];
  try {
    const parsed = JSON.parse(String(v));
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function spanText(row: Record<string, unknown>): string {
  const from = row.startDate ? String(row.startDate).slice(0, 10) : '';
  const to = row.endDate ? String(row.endDate).slice(0, 10) : '';
  return from || to ? `${from || '—'} ~ ${to || '—'}` : '—';
}

async function load() {
  if (!activeId.value) return;
  loading.value = true;
  try {
    result.value = await getResumeParse(activeId.value);
  } catch (e) {
    message.error(errorText(e, '解析结果加载失败'));
    result.value = null;
  } finally {
    loading.value = false;
  }
}

function locateById() {
  if (!idInput.value || idInput.value <= 0) {
    message.warning('请输入简历 ID');
    return;
  }
  activeId.value = idInput.value;
  result.value = null;
  load();
}

async function reparse() {
  if (!activeId.value) return;
  loading.value = true;
  try {
    result.value = await parseResume(activeId.value);
    message.success('重新解析完成');
  } catch (e) {
    message.error(errorText(e, '重新解析失败'));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (activeId.value) load();
});
</script>

<template>
  <a-drawer open title="解析结果" width="640" :mask-closable="true" @close="emit('close')">
    <a-spin :spinning="loading">
      <div v-if="!activeId" class="locate-box">
        <p class="locate-tip">候选人行未携带简历 ID，请输入简历 ID（rcu_resume.id）定位解析结果</p>
        <a-space>
          <a-input-number v-model:value="idInput" :min="1" placeholder="简历 ID" style="width: 160px" />
          <a-button type="primary" @click="locateById">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
        </a-space>
      </div>
      <template v-else-if="result">
        <a-alert
          v-if="isRuleLimited"
          type="warning"
          show-icon
          message="规则解析仅供参考，请人工核对"
          class="limited-alert"
        />
        <a-alert
          v-else-if="result.parseError"
          type="error"
          show-icon
          :message="`解析异常：${result.parseError}`"
          class="limited-alert"
        />

        <a-descriptions :column="1" size="small" bordered class="parsed-desc">
          <a-descriptions-item label="姓名">{{ result.parsedName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="手机号">{{ result.parsedMobile || '—' }}</a-descriptions-item>
          <a-descriptions-item label="邮箱">{{ result.parsedEmail || '—' }}</a-descriptions-item>
          <a-descriptions-item label="解析状态">
            <a-tag :color="isRuleLimited ? 'gold' : 'green'">
              {{ isRuleLimited ? '规则受限' : '解析成功' }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>

        <h4 class="block-title">教育经历</h4>
        <a-table
          v-if="educationRows.length"
          size="small"
          :pagination="false"
          :columns="eduColumns"
          :data-source="educationRows"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'span'">{{ spanText(record) }}</template>
          </template>
        </a-table>
        <EmptyHint v-else description="暂无教育经历" />

        <h4 class="block-title">工作经历</h4>
        <a-table
          v-if="experienceRows.length"
          size="small"
          :pagination="false"
          :columns="expColumns"
          :data-source="experienceRows"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'span'">{{ spanText(record) }}</template>
          </template>
        </a-table>
        <EmptyHint v-else description="暂无工作经历" />
      </template>
      <EmptyHint
        v-else-if="!loading"
        description="暂无解析结果"
        :hint="canWrite ? '点击下方「重新解析」生成解析结果' : ''"
      />
    </a-spin>

    <template #footer>
      <a-space v-if="activeId">
        <a-button :loading="loading" @click="reparse">
          <template #icon><ReloadOutlined /></template>
          重新解析
        </a-button>
        <a-button v-if="canWrite && result" type="primary" @click="fixOpen = true">人工修正</a-button>
      </a-space>
    </template>

    <ResumeParseFixModal
      v-model:open="fixOpen"
      :resume-id="activeId ?? 0"
      :result="result"
      @saved="result = $event"
    />
  </a-drawer>
</template>

<style scoped>
.limited-alert {
  margin-bottom: 16px;
}

.locate-box {
  padding: 8px 0;
}

.locate-tip {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
}

.parsed-desc {
  margin-bottom: 16px;
}

.block-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}
</style>
