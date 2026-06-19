<template>
  <div class="rename-overlay" @click.self="$emit('close')">
    <div class="rename-panel animate-fade-in-up">
      <h3 class="rename-title">重命名对话</h3>
      <div class="rename-input-row">
        <input v-model="newTitle" class="rename-input" placeholder="输入新标题" />
        <button class="magic-btn" @click="autoTitle" title="自动生成标题">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 4V2M15 16v2M8 9h2M20 9h2M17.8 11.8L19 13M17.8 6.2L19 5M12.2 11.8L11 13M12.2 6.2L11 5"/>
          </svg>
        </button>
      </div>
      <div class="rename-actions">
        <button class="cancel-btn" @click="$emit('close')">取消</button>
        <button class="confirm-btn" @click="handleRename" :disabled="!newTitle.trim()">确认</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { store } from '@/stores'

const props = defineProps<{ conversationId: string; currentTitle: string }>()
const emit = defineEmits<{ close: [] }>()

const newTitle = ref(props.currentTitle)

async function autoTitle() {
  // Try to use the first user message as title
  const messages = await store.loadMessages(props.conversationId)
  const firstUserMsg = store.state.messages.find(m => m.role === 'user')
  if (firstUserMsg) {
    newTitle.value = firstUserMsg.content.slice(0, 24)
  }
}

async function handleRename() {
  if (!newTitle.value.trim()) return
  await store.renameConversation(props.conversationId, newTitle.value.trim())
  emit('close')
}
</script>

<style scoped>
.rename-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}

.rename-panel {
  width: 380px;
  max-width: 90vw;
  border-radius: 18px;
  padding: 2rem;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-md);
}

.rename-title {
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 1.2rem;
}

.rename-input-row {
  display: flex;
  gap: 0.5rem;
}

.rename-input {
  flex: 1;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  padding: 0.65rem 0.8rem;
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: 0.9rem;
  outline: none;
  transition: all var(--transition-smooth);
}

.rename-input:focus {
  border-color: var(--input-focus-border);
  box-shadow: var(--input-focus-shadow);
}

.magic-btn {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-soft);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  color: var(--accent);
  cursor: pointer;
  transition: all var(--transition-smooth);
}

.magic-btn:hover {
  background: var(--accent-soft);
  border-color: var(--accent);
}

.rename-actions {
  display: flex;
  gap: 0.8rem;
  justify-content: flex-end;
  margin-top: 1.2rem;
}

.cancel-btn {
  padding: 0.6rem 1.2rem;
  font-family: var(--font-sans);
  font-size: 0.85rem;
  color: var(--text-secondary);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.confirm-btn {
  padding: 0.6rem 1.2rem;
  font-family: var(--font-sans);
  font-size: 0.85rem;
  color: white;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-smooth);
}

.confirm-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
