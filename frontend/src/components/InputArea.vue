<template>
  <div class="input-area">
    <div class="input-shell">
      <div class="input-wrapper" @click="focusTextarea">
        <textarea
          ref="textareaRef"
          v-model="inputText"
          class="chat-input"
          placeholder="给 AliAgent 发送消息"
          rows="1"
          @keydown.enter.exact="handleSend"
          @input="autoResize"
        ></textarea>

        <div class="input-toolbar">
          <button class="attach-btn" @click="triggerUpload" title="上传文档到知识库">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14"/>
            </svg>
          </button>
          <input
            ref="fileInputRef"
            type="file"
            accept=".pdf,.docx,.md,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/markdown,text/plain"
            class="file-input-hidden"
            @change="handleFileChange"
          />
          <span v-if="store.state.isLoading" class="loading-indicator">
            <span class="loading-dot"></span>
            正在回复
          </span>
          <button :class="['send-btn', { active: inputText.trim() }]" @click="handleSend" :disabled="!inputText.trim() || store.state.isLoading" title="发送">
            <svg v-if="store.state.isLoading" width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <rect x="6" y="6" width="12" height="12" rx="2"/>
            </svg>
            <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3">
              <path d="M12 19V5"/>
              <path d="M5 12l7-7 7 7"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
    <p class="input-hint">AliAgent 可能会出错，请核实重要信息。</p>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { store } from '@/stores'

const inputText = ref('')
const textareaRef = ref<HTMLTextAreaElement>()
const fileInputRef = ref<HTMLInputElement>()

function autoResize() {
  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.style.height = 'auto'
    textareaRef.value.style.height = Math.min(textareaRef.value.scrollHeight, 180) + 'px'
  })
}

function handleSend(e?: Event) {
  if (e) {
    const ke = e as KeyboardEvent
    if (ke.shiftKey) return
    ke.preventDefault()
  }
  if (!inputText.value.trim() || store.state.isLoading) return
  store.sendMessage(inputText.value.trim())
  inputText.value = ''
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
}

function triggerUpload() {
  fileInputRef.value?.click()
}

function focusTextarea() {
  textareaRef.value?.focus()
}

async function handleFileChange() {
  const file = fileInputRef.value?.files?.[0]
  if (!file) return

  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !['pdf', 'docx', 'md', 'txt'].includes(ext)) {
    store.addToast('仅支持 PDF / DOCX / MD / TXT 文件', 'error')
    return
  }

  try {
    await store.uploadDocument(file)
  } catch {
    // Error already handled by store
  } finally {
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}
</script>

<style scoped>
.input-area {
  position: absolute;
  left: var(--active-sidebar-width, var(--sidebar-width));
  right: 0;
  bottom: 0;
  z-index: 25;
  padding: 0 24px 14px;
  pointer-events: none;
}

.input-shell {
  width: min(780px, 100%);
  margin: 0 auto;
  pointer-events: auto;
}

.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-height: 96px;
  padding: 14px 14px 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 24px;
  box-shadow: var(--shadow-input);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--border-subtle);
  box-shadow: var(--shadow-input);
}

.input-area:focus,
.input-area:focus-visible,
.input-area:focus-within,
.input-shell:focus,
.input-shell:focus-visible,
.input-shell:focus-within,
.input-wrapper:focus,
.input-wrapper:focus-visible {
  outline: none;
}

.chat-input {
  width: 100%;
  flex: 1;
  min-height: 42px;
  max-height: 180px;
  resize: none;
  overflow-y: auto;
  border: none;
  outline: none;
  box-shadow: none;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: 15px;
  line-height: 1.55;
  padding: 0 4px;
}

.chat-input:focus,
.chat-input:focus-visible {
  outline: none;
  box-shadow: none;
}

.chat-input::placeholder {
  color: var(--text-tertiary);
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.attach-btn,
.send-btn {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast), transform var(--transition-fast);
}

.attach-btn {
  background: transparent;
  color: var(--text-primary);
}

.attach-btn:hover {
  background: var(--bg-surface-muted);
}

.file-input-hidden {
  display: none;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--accent);
  animation: pulse-aurora 1.2s ease-in-out infinite;
}

.send-btn {
  margin-left: auto;
  background: var(--text-tertiary);
  color: white;
}

.send-btn.active {
  background: var(--accent);
}

.send-btn.active:hover {
  background: var(--accent-hover);
  transform: translateY(-1px);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.input-hint {
  width: min(780px, 100%);
  margin: 7px auto 0;
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: center;
  pointer-events: none;
}

@media (max-width: 768px) {
  .input-area {
    left: 0;
    padding: 0 12px 12px;
  }

  .input-wrapper {
    min-height: 92px;
    border-radius: 22px;
  }
}
</style>
