<script setup lang="ts">
/**
 * 自助端-通知偏好：按渠道（钉钉/企业微信/Webhook）开关推送并配置免打扰时段。
 * 每个渠道独立保存（PUT /plt/notify-preferences/me 为单渠道 upsert 语义）。
 */
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  fetchMyNotifyPreferences,
  updateMyNotifyPreference,
} from '../../api/platform';
import type { NotifyChannel, NotifyPreference } from '../../api/platform';
import { errorText } from '../../components/admin/crud-helpers';

interface ChannelMeta {
  channel: NotifyChannel;
  title: string;
  desc: string;
}

const CHANNELS: ChannelMeta[] = [
  { channel: 'DINGTALK', title: '钉钉', desc: '审批与考勤提醒推送到钉钉' },
  { channel: 'WECOM', title: '企业微信', desc: '审批与考勤提醒推送到企业微信' },
  { channel: 'WEBHOOK', title: 'Webhook', desc: '推送至自定义接收地址' },
];

interface FormState {
  enabled: boolean;
  quietRange: string[];
}

const states = reactive<Record<string, FormState>>({});
CHANNELS.forEach((c) => {
  states[c.channel] = { enabled: false, quietRange: [] };
});

const loading = ref(false);
const saving = reactive<Record<string, boolean>>({});

async function load() {
  loading.value = true;
  try {
    const prefs = await fetchMyNotifyPreferences();
    CHANNELS.forEach((c) => {
      const pref = prefs.find((p) => p.channel === c.channel);
      states[c.channel] = {
        enabled: pref?.enabled ?? false,
        quietRange: pref?.quietStart && pref?.quietEnd ? [pref.quietStart, pref.quietEnd] : [],
      };
    });
  } catch (e) {
    message.error(errorText(e, '通知偏好加载失败'));
  } finally {
    loading.value = false;
  }
}

async function save(meta: ChannelMeta) {
  const state = states[meta.channel];
  if (state.quietRange.length && (!state.quietRange[0] || !state.quietRange[1])) {
    message.warning('请补全免打扰时段');
    return;
  }
  saving[meta.channel] = true;
  try {
    await updateMyNotifyPreference({
      channel: meta.channel,
      enabled: state.enabled,
      ...(state.quietRange[0] ? { quietStart: state.quietRange[0] } : {}),
      ...(state.quietRange[1] ? { quietEnd: state.quietRange[1] } : {}),
    });
    message.success(`${meta.title}偏好已保存`);
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    saving[meta.channel] = false;
  }
}

function timeText(value: string): string {
  return String(value).slice(0, 5);
}

onMounted(load);
</script>

<template>
  <div class="notify-page">
    <section class="head">
      <div>
        <h2 class="head-title">通知偏好</h2>
        <p class="head-sub">选择接收提醒的渠道，并可配置免打扰时段，避免休息时间被打扰</p>
      </div>
    </section>

    <a-spin :spinning="loading">
      <a-row :gutter="[16, 16]">
        <a-col v-for="meta in CHANNELS" :key="meta.channel" :xs="24" :md="8">
          <a-card :bordered="false" class="channel-card">
            <div class="channel-head">
              <div>
                <div class="channel-title">{{ meta.title }}</div>
                <div class="channel-desc">{{ meta.desc }}</div>
              </div>
              <a-switch v-model:checked="states[meta.channel].enabled" />
            </div>
            <a-divider class="divider" />
            <div class="quiet-label">免打扰时段</div>
            <a-time-range-picker
              v-model:value="states[meta.channel].quietRange"
              value-format="HH:mm:ss"
              format="HH:mm"
              placeholder="['开始', '结束']"
              style="width: 100%; margin-top: 8px"
            />
            <div v-if="states[meta.channel].quietRange[0]" class="quiet-text">
              {{ timeText(states[meta.channel].quietRange[0]) }} 至
              {{ timeText(states[meta.channel].quietRange[1] || '') }} 静音
            </div>
            <a-button
              type="primary"
              ghost
              block
              class="save-btn"
              :loading="saving[meta.channel]"
              @click="save(meta)"
            >
              保存{{ meta.title }}偏好
            </a-button>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<style scoped>
.notify-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
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

.channel-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.channel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.channel-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.channel-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.divider {
  margin: var(--space-4) 0;
}

.quiet-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.quiet-text {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--color-text-muted);
}

.save-btn {
  margin-top: var(--space-4);
}
</style>
