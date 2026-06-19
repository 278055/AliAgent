<template>
  <div
    :class="['file-uploader', { dragging, uploading }]"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="handleDrop"
    @click="triggerInput"
  >
    <input
      ref="fileInputRef"
      type="file"
      :accept="ACCEPTED_TYPES"
      class="file-input"
      @change="handleFileChange"
    />

    <template v-if="!uploading">
      <div class="upload-icon">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
          <path d="M17 8l-5-5-5 5"/>
          <path d="M12 3v12"/>
        </svg>
      </div>
      <p class="upload-label">拖拽文件到此处或 <span>点击选择</span></p>
      <p class="upload-hint">PDF、DOCX、MD、TXT，最大 50MB</p>
    </template>

    <template v-else>
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="upload-spinner">
        <circle cx="12" cy="12" r="10" stroke-opacity="0.25"/>
        <path d="M12 2a10 10 0 019.95 9" stroke-linecap="round"/>
      </svg>
      <p class="upload-label">正在上传 {{ progress }}%</p>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
    </template>

    <p v-if="errorMsg" class="upload-error">{{ errorMsg }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { store } from '@/stores'

const ACCEPTED_TYPES = '.pdf,.docx,.md,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/markdown,text/plain'
const MAX_SIZE = 50 * 1024 * 1024

const dragging = ref(false)
const uploading = ref(false)
const progress = ref(0)
const errorMsg = ref('')
const fileInputRef = ref<HTMLInputElement>()

const emit = defineEmits<{
  uploaded: [doc: any]
}>()

function triggerInput() {
  if (!uploading.value) {
    fileInputRef.value?.click()
  }
}

function validateFile(file: File): boolean {
  const ext = file.name.split('.').pop()?.toLowerCase()
  const allowedExts = ['pdf', 'docx', 'md', 'txt']
  if (!ext || !allowedExts.includes(ext)) {
    errorMsg.value = '仅支持 PDF / DOCX / MD / TXT'
    return false
  }
  if (file.size > MAX_SIZE) {
    errorMsg.value = '文件大小不能超过 50MB'
    return false
  }
  errorMsg.value = ''
  return true
}

async function uploadFile(file: File) {
  if (!validateFile(file)) return

  uploading.value = true
  progress.value = 0
  errorMsg.value = ''

  try {
    const doc = await store.uploadDocument(file)
    emit('uploaded', doc)
  } catch (e: any) {
    errorMsg.value = e.message || '上传失败'
  } finally {
    uploading.value = false
    progress.value = 0
  }

  if (fileInputRef.value) fileInputRef.value.value = ''
}

function handleDrop(e: DragEvent) {
  dragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) uploadFile(file)
}

function handleFileChange() {
  const file = fileInputRef.value?.files?.[0]
  if (file) uploadFile(file)
}
</script>

<style scoped>
.file-uploader {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 154px;
  padding: 18px;
  border: 1.5px dashed var(--border-strong);
  border-radius: 14px;
  background: var(--bg-surface-muted);
  color: var(--text-secondary);
  text-align: center;
  cursor: pointer;
  transition: border-color var(--transition-fast), background var(--transition-fast);
}

.file-uploader:hover,
.file-uploader.dragging {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.file-uploader.uploading {
  cursor: default;
  border-color: var(--border-strong);
  background: var(--bg-surface-muted);
}

.file-input {
  display: none;
}

.upload-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--bg-surface);
  color: var(--accent);
  box-shadow: var(--shadow-xs);
}

.upload-label {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 550;
}

.upload-label span {
  color: var(--accent);
}

.upload-hint {
  color: var(--text-tertiary);
  font-size: 12px;
}

.upload-error {
  color: var(--danger);
  font-size: 12px;
}

.progress-bar {
  width: 100%;
  height: 5px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--border-subtle);
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
  transition: width 0.25s ease;
}

.upload-spinner {
  color: var(--accent);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
