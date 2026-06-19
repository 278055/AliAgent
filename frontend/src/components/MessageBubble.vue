<template>
  <div :class="['message-bubble', message.role, { streaming: isStreaming }]" :style="{ animationDelay: animationDelay + 's' }">
    <div class="bubble-body">
      <div v-if="isStreaming" class="bubble-content markdown-content">
        <div v-html="renderMarkdown(streamingContent || '')"></div>
        <span class="stream-cursor"></span>
      </div>
      <div v-else class="bubble-content markdown-content" v-html="renderMarkdown(message.content)"></div>

      <div v-if="message.role === 'assistant'" class="message-actions">
        <button title="复制" @click="copyMessage">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2"/>
            <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
          </svg>
        </button>
        <span class="bubble-time">{{ formatRelativeTime(message.createdAt) }}</span>
      </div>

      <div v-if="message.sources && message.sources.length > 0 && message.role === 'assistant'" class="bubble-sources">
        <div class="sources-label">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/>
          </svg>
          <span>引用来源 {{ message.sources.length }}</span>
        </div>
        <RAGSourceCard
          v-for="(source, i) in message.sources"
          :key="source.chunkId || i"
          :source="source"
          :animation-delay="i * 0.05"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { renderMarkdown } from '@/utils/markdown'
import { formatRelativeTime } from '@/utils/time'
import { store } from '@/stores'
import RAGSourceCard from '@/components/RAGSourceCard.vue'
import type { Message } from '@/types'

const props = defineProps<{
  message: Message
  isStreaming: boolean
  streamingContent: string
  animationDelay: number
}>()

async function copyMessage() {
  try {
    await navigator.clipboard.writeText(props.isStreaming ? props.streamingContent : props.message.content)
    store.addToast('已复制', 'success')
  } catch {
    store.addToast('复制失败', 'error')
  }
}
</script>

<style scoped>
.message-bubble {
  width: 100%;
  display: flex;
  opacity: 0;
  animation: stream-in 0.28s ease forwards;
}

.message-bubble.user {
  justify-content: flex-end;
}

.message-bubble.assistant {
  justify-content: flex-start;
}

.bubble-body {
  min-width: 0;
  max-width: 100%;
}

.message-bubble.user .bubble-body {
  max-width: min(70%, 560px);
}

.message-bubble.assistant .bubble-body {
  width: 100%;
}

.bubble-content {
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: 15px;
  line-height: 1.72;
  word-break: break-word;
}

.message-bubble.user .bubble-content {
  padding: 11px 15px;
  background: var(--bg-message-user);
  border-radius: 18px;
  color: var(--text-primary);
}

.message-bubble.assistant .bubble-content {
  padding: 0;
  background: transparent;
}

.stream-cursor {
  display: inline-block;
  width: 7px;
  height: 16px;
  margin-left: 2px;
  border-radius: 2px;
  background: var(--accent);
  animation: blink 0.85s ease-in-out infinite;
  vertical-align: middle;
}

@keyframes blink {
  0%, 52% { opacity: 1; }
  53%, 100% { opacity: 0; }
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  margin-top: 6px;
  color: var(--text-tertiary);
}

.message-actions button {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.message-actions button:hover {
  background: var(--bg-surface-muted);
  color: var(--text-primary);
}

.bubble-time {
  font-size: 12px;
}

.bubble-sources {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sources-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: 12px;
}

@media (max-width: 560px) {
  .message-bubble.user .bubble-body {
    max-width: 86%;
  }

  .bubble-content {
    font-size: 14px;
  }
}
</style>
