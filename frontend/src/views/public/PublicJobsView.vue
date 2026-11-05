<script setup lang="ts">
/**
 * 官网公开职位页（RCS-F-004）：免登录浏览在招职位并发起投递。
 * 独立公开段路由（/public/jobs），不受 MainLayout 与登录守卫管控。
 */
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { EnvironmentOutlined, ReloadOutlined } from '@ant-design/icons-vue';
import { applyPublicJob, listPublicJobs, resolveApplyFailText } from '../../api/publicRecruit';
import type { PublicJobItem } from '../../api/publicRecruit';
import { BizError } from '../../api/error';

const jobs = ref<PublicJobItem[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    jobs.value = await listPublicJobs();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '职位加载失败，请稍后重试');
    jobs.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/* ------------------------------ 投递弹窗 ------------------------------ */

const applyOpen = ref(false);
const applying = ref(false);
const appliedJob = ref<PublicJobItem | null>(null);
const applyResult = reactive({ applicationId: null as number | null, failedText: '' });
const form = reactive({ name: '', mobile: '', email: '', selfIntro: '' });

function openApply(job: PublicJobItem) {
  appliedJob.value = job;
  form.name = '';
  form.mobile = '';
  form.email = '';
  form.selfIntro = '';
  applyResult.applicationId = null;
  applyResult.failedText = '';
  applyOpen.value = true;
}

function jobTitle(job: PublicJobItem): string {
  return job.title || job.reqNo;
}

function validate(): string | null {
  if (!form.name.trim()) return '请填写姓名';
  if (!form.mobile.trim()) return '请填写手机号';
  if (!/^1\d{10}$/.test(form.mobile.trim())) return '手机号格式不正确';
  if (!form.email.trim()) return '请填写邮箱';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return '邮箱格式不正确';
  return null;
}

async function handleSubmit() {
  if (!appliedJob.value) return;
  const invalid = validate();
  if (invalid) {
    message.warning(invalid);
    return;
  }
  applying.value = true;
  try {
    const result = await applyPublicJob(appliedJob.value.reqId, {
      name: form.name.trim(),
      mobile: form.mobile.trim(),
      email: form.email.trim(),
      ...(form.selfIntro.trim() ? { selfIntro: form.selfIntro.trim() } : {}),
    });
    applyResult.applicationId = result.applicationId;
    applyResult.failedText = '';
  } catch (e) {
    const code = e instanceof BizError ? e.code : 0;
    applyResult.failedText =
      resolveApplyFailText(code) ?? (e instanceof Error ? e.message : '投递失败，请稍后重试');
  } finally {
    applying.value = false;
  }
}
</script>

<template>
  <div class="public-jobs">
    <header class="hero">
      <h1 class="hero-title">加入我们</h1>
      <p class="hero-sub">浏览在招职位，找到属于你的位置</p>
    </header>

    <section class="toolbar">
      <span class="toolbar-count">共 {{ jobs.length }} 个在招职位</span>
      <a-button size="small" :loading="loading" @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-spin :spinning="loading">
      <a-row v-if="jobs.length" :gutter="[16, 16]">
        <a-col v-for="job in jobs" :key="job.reqId" :xs="24" :sm="12" :lg="8">
          <a-card hoverable :bordered="false" class="job-card">
            <div class="job-name">{{ jobTitle(job) }}</div>
            <div class="job-meta">
              <span class="job-no">{{ job.reqNo }}</span>
              <span>招 {{ job.headcount }} 人</span>
            </div>
            <div class="job-foot">
              <span class="job-date">
                <EnvironmentOutlined /> 发布于 {{ String(job.createdAt || '').slice(0, 10) }}
              </span>
              <a-button type="primary" size="small" @click="openApply(job)">投递简历</a-button>
            </div>
          </a-card>
        </a-col>
      </a-row>
      <a-empty v-else-if="!loading" description="暂无在招职位" class="empty" />
    </a-spin>

    <a-modal
      v-model:open="applyOpen"
      :title="appliedJob ? `投递：${jobTitle(appliedJob)}` : '投递简历'"
      :footer="applyResult.applicationId !== null || applyResult.failedText ? null : undefined"
      :confirm-loading="applying"
      :mask-closable="false"
      ok-text="提交投递"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-result
        v-if="applyResult.applicationId !== null"
        status="success"
        title="投递成功"
        :sub-title="`受理编号 ${applyResult.applicationId}，我们会尽快与你联系，请保持通讯畅通。`"
      />
      <a-result v-else-if="applyResult.failedText" status="warning" title="投递未成功" :sub-title="applyResult.failedText" />
      <a-form v-else :model="form" layout="vertical">
        <a-form-item label="姓名" required>
          <a-input v-model:value="form.name" :maxlength="64" placeholder="请输入真实姓名" />
        </a-form-item>
        <a-form-item label="手机号" required>
          <a-input v-model:value="form.mobile" :maxlength="11" placeholder="请输入 11 位手机号" />
        </a-form-item>
        <a-form-item label="邮箱" required>
          <a-input v-model:value="form.email" :maxlength="128" placeholder="用于接收面试通知" />
        </a-form-item>
        <a-form-item label="自我介绍">
          <a-textarea v-model:value="form.selfIntro" :rows="4" :maxlength="500" placeholder="选填，简要介绍工作经历与优势（500 字内）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.public-jobs {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-4) var(--space-10);
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.hero-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-count {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.job-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.job-name {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.job-meta {
  display: flex;
  gap: var(--space-4);
  margin-top: var(--space-2);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.job-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-4);
}

.job-date {
  font-size: 12px;
  color: var(--color-text-muted);
}

.empty {
  padding: var(--space-10) 0;
}
</style>
