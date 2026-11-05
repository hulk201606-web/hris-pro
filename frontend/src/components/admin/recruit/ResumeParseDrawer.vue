<script setup lang="ts">
/**
 * 简历解析结果抽屉：展示 parsed 三字段 + 教育/工作经历 JSON 数组表格，
 * 支持规则重跑 / AI 解析与人工修正（ResumeParseFixModal）。
 * 两种定位模式：
 * - 传 candidateId（候选人行联动，批 6 #8）：自动查候选人简历（GET /rcu/candidates/{id}/resumes）
 *   与最新解析（GET /rcu/candidates/{id}/resume-parse，无解析时后端 NOT_FOUND）；
 * - 传 resumeId / 抽屉内输入 ID：走 GET /rcu/resumes/{id}/parse。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../EmptyHint.vue';
import ResumeParseFixModal from './ResumeParseFixModal.vue';
import { errorText } from '../crud-helpers';
import {
  getCandidateResumeParse,
  getResumeParse,
  listCandidateResumes,
  parseResume,
  parseResumeAi,
} from '../../../api/recruit';
import type { ResumeParseResult } from '../../../types/api-batch2';

const props = defineProps<{
  /** 简历 ID（rcu_resume.id），可为空（由抽屉内输入定位） */
  resumeId?: number;
  /** 候选人 ID（批 6 #8）：自动联动其简历与最新解析结果 */
  candidateId?: number;
  /** 是否可写（重新解析 / AI 解析 / 人工修正），对应 rcu:resume:write */
  canWrite: boolean;
}>();

const emit = defineEmits<{ (e: 'close'): void }>();

const activeId = ref<number | null>(props.resumeId ?? null);
const idInput = ref<number | undefined>(undefined);
const loading = ref(false);
const parsing = ref(false);
const noResume = ref(false);
const result = ref<ResumeParseResult | null>(null);
const fixOpen = ref(false);

const educationRows = computed(() => parseList(result.value?.educationJson).map((r, i) => ({ ...r, key: i })));
const experienceRows = computed(() => parseList(result.value?.experienceJson).map((r, i) => ({ ...r, key: i })));

/** 解析状态 tag：AI_PARSED 蓝色（批 6 #8）/ RULE_LIMITED 金色警示 / 其余绿色成功 */
const statusTag = computed(() => {
  const s = result.value?.parseStatus;
  if (s === 'AI_PARSED') return { color: 'blue', text: 'AI 解析' };
  if (s === 'RULE_LIMITED') return { color: 'gold', text: '规则受限' };
  return { color: 'green', text: '解析成功' };
});

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

/**
 * 候选人联动加载：查其简历列表定位简历 ID，再取最新解析结果；
 * 无解析（后端 NOT_FOUND）时 result 置空，引导用 AI 解析生成。
 */
async function loadForCandidate() {
  loading.value = true;
  try {
    const resumes = await listCandidateResumes(props.candidateId as number);
    if (!resumes.length) {
      noResume.value = true;
      return;
    }
    const latest = [...resumes].sort((a, b) =>
      String(b.updatedAt ?? b.createdAt ?? '').localeCompare(String(a.updatedAt ?? a.createdAt ?? '')),
    )[0];
    activeId.value = latest.id;
    try {
      result.value = await getCandidateResumeParse(props.candidateId as number);
    } catch {
      result.value = null;
    }
  } catch (e) {
    message.error(errorText(e, '候选人简历加载失败'));
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

/** 规则解析重跑（POST /rcu/resumes/{id}/parse，缺省 mode=rule） */
async function reparse() {
  if (!activeId.value) return;
  parsing.value = true;
  try {
    result.value = await parseResume(activeId.value);
    message.success('重新解析完成');
  } catch (e) {
    message.error(errorText(e, '重新解析失败'));
  } finally {
    parsing.value = false;
  }
}

/** AI 解析（POST /rcu/resumes/{id}/parse?mode=ai）：LLM 结构化抽取，失败时旧结果保持不变 */
async function aiParse() {
  if (!activeId.value) return;
  parsing.value = true;
  try {
    result.value = await parseResumeAi(activeId.value);
    message.success('AI 解析完成');
  } catch (e) {
    message.error(errorText(e, 'AI 解析失败'));
  } finally {
    parsing.value = false;
  }
}

onMounted(() => {
  if (props.candidateId) loadForCandidate();
  else if (activeId.value) load();
});
</script>

<template>
  <a-drawer open title="解析结果" width="640" :mask-closable="true" @close="emit('close')">
    <a-spin :spinning="loading || parsing">
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
      <EmptyHint
        v-else-if="noResume"
        description="该候选人暂无简历"
        hint="请先在简历入库后查看解析结果"
      />
      <template v-else-if="result">
        <a-alert
          v-if="result.parseStatus === 'RULE_LIMITED'"
          type="warning"
          show-icon
          message="规则解析仅供参考，可点击「AI 解析」提升准确度"
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
            <a-tag :color="statusTag.color">{{ statusTag.text }}</a-tag>
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
        :hint="canWrite ? '点击下方「AI 解析」生成解析结果' : ''"
      />
    </a-spin>

    <template #footer>
      <a-space v-if="activeId && !noResume">
        <a-button :loading="parsing" @click="reparse">
          <template #icon><ReloadOutlined /></template>
          重新解析
        </a-button>
        <a-button v-if="canWrite" type="primary" :loading="parsing" @click="aiParse">
          AI 解析
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
