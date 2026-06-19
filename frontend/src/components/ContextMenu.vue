<template>
  <div class="context-menu" :style="{ left: x + 'px', top: y + 'px' }" @click.stop>
    <button class="ctx-btn" @click="handleRename">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
        <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/>
      </svg>
      <span>重命名</span>
    </button>
    <button class="ctx-btn" @click="handlePin">
      <svg v-if="conversation?.pinned" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M3 3l7.07 16.97 2.51-7.39 7.39-2.51L3 3z"/><path d="M13 13l6 6"/>
      </svg>
      <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="12" y1="17" x2="12" y2="22"/><path d="M5 17h14v-1.76a2 2 0 00-1.11-1.79L12 10l-5.89 3.45A2 2 0 005 15.24z"/>
      </svg>
      <span>{{ conversation?.pinned ? '取消置顶' : '置顶' }}</span>
    </button>
    <div class="ctx-divider"></div>
    <button class="ctx-btn ctx-danger" @click="handleDelete">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
      </svg>
      <span>删除</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { store } from '@/stores'
import type { Conversation } from '@/types'
import RenameModal from '@/components/RenameModal.vue'

const props = defineProps<{
  x: number
  y: number
  conversation: Conversation
}>()

const emit = defineEmits<{ close: [] }>()

function handleRename() {
  emit('close')
  // Show rename modal via store
  store.addToast('请使用重命名功能', 'info')
}

function handlePin() {
  emit('close')
  store.pinConversation(props.conversation.id)
}

function handleDelete() {
  emit('close')
  store.deleteConversation(props.conversation.id)
}
</script>

<style scoped>
.context-menu {
  position: fixed;
  z-index: 200;
  min-width: 160px;
  padding: 6px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
}

.ctx-btn {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  height: 36px;
  padding: 0 10px;
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--text-primary);
  background: transparent;
  border: none;
  border-radius: 9px;
  cursor: pointer;
  width: 100%;
  transition: all var(--transition-fast);
}

.ctx-btn:hover {
  background: var(--bg-surface-muted);
  color: var(--text-primary);
}

.ctx-danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.ctx-divider {
  height: 1px;
  background: var(--border-subtle);
  margin: 0.3rem 0;
}
</style>
