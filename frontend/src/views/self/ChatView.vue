<script setup lang="ts">
/**
 * 智能问答（自助）：KBD-F-008/009/010/011/012。
 * 左侧会话列表（本人会话），右侧对话区：问答气泡、citations 折叠来源、
 * 拒答样式区分（answerStatus=NO_RECALL 用警示样式）、有用/无用反馈。
 */
import { nextTick, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  LikeOutlined, LikeFilled, DislikeOutlined, DislikeFilled,
  PlusOutlined, SendOutlined, FileTextOutlined,
} from '@ant-design/icons-vue';
import { errorText } from '../../components/admin/crud-helpers';
import {
  askQa, createQaSession, feedbackQa, listQaMessages, listQaSessions,
  type QaCitation, type QaMessage, type QaSession,
} from '../../api/knowledge';

interface ChatBubble {
  key: string;
  role: 'user' | 'assistant';
  text: string;
  msgId?: number;
  citations?: QaCitation[] | null;
  noRecall?: boolean;
  feedback?: 'LIKE' | 'DISLIKE' | null;
}

const sessions = ref<QaSession[]>([]);
const currentSessionId = ref<number | null>(null);
const bubbles = ref<ChatBubble[]>([]);
const input = ref('');
const asking = ref(false);
const loadingHistory = ref(false);
const listEl = ref<HTMLElement | null>(null);

async function loadSessions() {
  sessions.value = (await listQaSessions()) ?? [];
  if (sessions.value.length > 0 && currentSessionId.value === null) {
    await openSession(sessions.value[0].id);
  }
}

async function openSession(id: number) {
  currentSessionId.value = id;
  loadingHistory.value = true;
  try {
    const rows = (await listQaMessages(id)) ?? [];
    bubbles.value = [];
    for (const m of rows) {
      if (m.question) bubbles.value.push({ key: `q-${m.id}`, role: 'user', text: m.question });
      if (m.answer) {
        bubbles.value.push({
          key: `a-${m.id}`, role: 'assistant', text: m.answer, msgId: m.id,
          citations: m.citations, noRecall: m.answerStatus === 'NO_RECALL',
        });
      }
    }
    await scrollBottom();
  } catch (e) {
    message.error(errorText(e, '会话历史加载失败'));
  } finally {
    loadingHistory.value = false;
  }
}

async function newSession() {
  try {
    const s = await createQaSession({});
    sessions.value = [s, ...sessions.value];
    currentSessionId.value = s.id;
    bubbles.value = [];
  } catch (e) {
    message.error(errorText(e, '创建会话失败'));
  }
}

async function send() {
  const q = input.value.trim();
  if (!q || asking.value) return;
  let sid = currentSessionId.value;
  if (sid === null) {
    try {
      const s = await createQaSession({});
      sessions.value = [s, ...sessions.value];
      sid = s.id;
      currentSessionId.value = s.id;
    } catch (e) {
      message.error(errorText(e, '创建会话失败'));
      return;
    }
  }
  input.value = '';
  bubbles.value.push({ key: `u-${Date.now()}`, role: 'user', text: q });
  await scrollBottom();
  asking.value = true;
  try {
    const m = await askQa(sid, q);
    bubbles.value.push({
      key: `a-${m.id}`, role: 'assistant', text: m.answer ?? '', msgId: m.id,
      citations: m.citations, noRecall: m.answerStatus === 'NO_RECALL',
    });
    // 新会话首问后后端会用问题改写标题，刷新侧栏
    await loadSessionsTitle(sid);
    await scrollBottom();
  } catch (e) {
    message.error(errorText(e, '问答请求失败'));
  } finally {
    asking.value = false;
  }
}

async function loadSessionsTitle(sid: number) {
  try {
    sessions.value = (await listQaSessions()) ?? [];
    const s = sessions.value.find((x) => x.id === sid);
    if (s && s.title && !s.title.startsWith('问答会话')) {
      // 标题已被后端更新，无需额外处理
    }
  } catch {
    /* 标题刷新失败不影响主流程 */
  }
}

async function sendFeedback(b: ChatBubble, helpful: boolean) {
  if (!b.msgId) return;
  const next = helpful ? 'LIKE' : 'DISLIKE';
  try {
    await feedbackQa(b.msgId, helpful);
    b.feedback = b.feedback === next ? null : next;
    message.success(b.feedback ? '感谢您的反馈' : '已取消反馈');
  } catch (e) {
    message.error(errorText(e, '反馈提交失败'));
  }
}

async function scrollBottom() {
  await nextTick();
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight;
}

onMounted(async () => {
  try {
    await loadSessions();
  } catch (e) {
    message.error(errorText(e, '会话列表加载失败'));
  }
});
</script>

<template>
  <div class="chat-view">
    <aside class="chat-side">
      <button class="new-btn" type="button" @click="newSession">
        <PlusOutlined /> 新建会话
      </button>
      <div class="side-list">
        <button
          v-for="s in sessions" :key="s.id" type="button" class="side-item"
          :class="{ active: s.id === currentSessionId }" @click="openSession(s.id)"
        >
          <FileTextOutlined class="side-icon" />
          <span class="side-title">{{ s.title || '未命名会话' }}</span>
        </button>
        <p v-if="sessions.length === 0" class="side-empty">暂无会话，点击上方新建</p>
      </div>
    </aside>

    <section class="chat-main">
      <header class="chat-head">
        <h2 class="chat-title">智能问答</h2>
        <p class="chat-sub">基于知识库检索回答，答案标注来源编号；知识未收录时明确拒答</p>
      </header>

      <div ref="listEl" class="chat-list">
        <a-spin v-if="loadingHistory" class="history-spin" />
        <p v-if="bubbles.length === 0 && !loadingHistory" class="chat-empty">
          输入问题开始提问，例如「年假有多少天」
        </p>
        <template v-for="b in bubbles" :key="b.key">
          <div class="bubble-row" :class="b.role">
            <div v-if="b.role === 'user'" class="bubble user-bubble">{{ b.text }}</div>
            <div v-else class="bubble assistant-bubble" :class="{ 'no-recall': b.noRecall }">
              <div class="answer-text">{{ b.text }}</div>
              <a-collapse v-if="b.citations && b.citations.length" ghost class="cite-box">
                <a-collapse-panel key="cites" :header="`参考来源（${b.citations.length}）`">
                  <div v-for="(c, i) in b.citations" :key="c.chunkId" class="cite-item">
                    <span class="cite-no">[{{ i + 1 }}]</span>
                    {{ c.title || '未知文档' }}
                    <span class="cite-score">相似度 {{ (c.score ?? 0).toFixed(3) }}</span>
                  </div>
                </a-collapse-panel>
              </a-collapse>
              <div v-if="!b.noRecall" class="fb-bar">
                <span class="fb-label">这条回答有帮助吗？</span>
                <button type="button" class="fb-btn" :class="{ on: b.feedback === 'LIKE' }" @click="sendFeedback(b, true)">
                  <LikeOutlined v-if="b.feedback !== 'LIKE'" /><LikeFilled v-else />
                </button>
                <button type="button" class="fb-btn" :class="{ on: b.feedback === 'DISLIKE' }" @click="sendFeedback(b, false)">
                  <DislikeOutlined v-if="b.feedback !== 'DISLIKE'" /><DislikeFilled v-else />
                </button>
              </div>
            </div>
          </div>
        </template>
        <p v-if="asking" class="thinking">正在检索知识库并生成回答...</p>
      </div>

      <footer class="chat-input">
        <a-textarea
          v-model:value="input" :rows="2" placeholder="输入您的问题，Enter 发送"
          :disabled="asking" @keydown.enter.exact.prevent="send"
        />
        <a-button type="primary" :loading="asking" @click="send">
          <SendOutlined /> 发送
        </a-button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.chat-view { display: flex; height: calc(100vh - 140px); min-height: 480px; gap: var(--space-4, 16px); }
.chat-side { width: 240px; flex-shrink: 0; display: flex; flex-direction: column; gap: 12px; }
.new-btn { border: 1px dashed var(--color-border, #d9d9d9); background: transparent; border-radius: 8px; padding: 8px; cursor: pointer; color: var(--color-text, #333); }
.new-btn:hover { border-color: var(--color-primary, #1677ff); color: var(--color-primary, #1677ff); }
.side-list { overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.side-item { display: flex; align-items: center; gap: 8px; text-align: left; border: 0; background: transparent; padding: 10px; border-radius: 8px; cursor: pointer; color: var(--color-text, #333); }
.side-item:hover { background: var(--color-bg-hover, #f5f5f5); }
.side-item.active { background: var(--color-bg-active, #e6f4ff); color: var(--color-primary, #1677ff); }
.side-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.side-empty { color: var(--color-text-secondary, #999); font-size: 12px; padding: 8px; }
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-title { margin: 0; font-size: 20px; font-weight: 600; }
.chat-sub { margin: 4px 0 0; color: var(--color-text-secondary, #999); font-size: 13px; }
.chat-list { flex: 1; overflow-y: auto; padding: 16px 4px; display: flex; flex-direction: column; gap: 14px; }
.chat-empty, .thinking { color: var(--color-text-secondary, #999); text-align: center; }
.history-spin { margin: 0 auto; }
.bubble-row { display: flex; }
.bubble-row.user { justify-content: flex-end; }
.bubble { max-width: 72%; padding: 10px 14px; border-radius: 12px; line-height: 1.6; white-space: pre-wrap; }
.user-bubble { background: var(--color-primary, #1677ff); color: #fff; border-bottom-right-radius: 4px; }
.assistant-bubble { background: var(--color-bg-secondary, #f7f8fa); color: var(--color-text, #333); border-bottom-left-radius: 4px; }
.assistant-bubble.no-recall { background: var(--color-warning-bg, #fff7e6); border: 1px solid var(--color-warning-border, #ffd591); }
.answer-text { word-break: break-word; }
.cite-box { margin-top: 8px; }
.cite-item { font-size: 12px; color: var(--color-text-secondary, #666); padding: 2px 0; }
.cite-no { color: var(--color-primary, #1677ff); margin-right: 6px; }
.cite-score { margin-left: 8px; font-size: 11px; opacity: 0.7; }
.fb-bar { margin-top: 8px; display: flex; align-items: center; gap: 6px; }
.fb-label { font-size: 12px; color: var(--color-text-secondary, #999); }
.fb-btn { border: 0; background: transparent; cursor: pointer; padding: 4px 6px; border-radius: 6px; color: var(--color-text-secondary, #999); }
.fb-btn:hover, .fb-btn.on { color: var(--color-primary, #1677ff); background: var(--color-bg-hover, #f0f0f0); }
.chat-input { display: flex; gap: 12px; align-items: flex-end; padding-top: 12px; border-top: 1px solid var(--color-border, #f0f0f0); }
.chat-input :deep(.ant-btn) { height: 48px; }
</style>
