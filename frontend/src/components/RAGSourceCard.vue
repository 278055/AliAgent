<template>
  <div :class="['source-card', { expanded }]" @click="expanded = !expanded">
    <div class="source-header">
      <div class="source-doc-info">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
        <span class="source-doc-name">{{ source.documentName || '未知文档' }}</span>
        <span v-if="source.sectionTitle" class="source-section">{{ source.sectionTitle }}</span>
      </div>
      <span class="source-score">{{ Math.round(source.score * 100) }}%</span>
    </div>

    <transition name="collapse">
      <div v-if="expanded" class="source-body">
        <p class="source-content">{{ truncatedContent }}</p>
        <div v-if="source.documentId" class="source-actions">
          <button class="source-open-btn" title="打开知识库中的原始出处" @click.stop="openPreview">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 3h7v7" />
              <path d="M10 14L21 3" />
              <path d="M21 14v5a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h5" />
            </svg>
            <span>查看出处</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { store } from '@/stores'
import type { RAGSource } from '@/types'

const props = defineProps<{
  source: RAGSource
  animationDelay?: number
}>()

const expanded = ref(false)
const maxContentLen = 220

const truncatedContent = computed(() => {
  const content = props.source.content || ''
  return content.length > maxContentLen ? content.slice(0, maxContentLen) + '...' : content
})

function openPreview() {
  store.openDocumentPreview(props.source.documentId, props.source.chunkId)
}
</script>

<style scoped>
.source-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px 10px 13px;
  background: var(--bg-surface-muted);
  border: 1px solid var(--border-subtle);
  border-left: 3px solid var(--accent);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color var(--transition-fast), background var(--transition-fast);
}

.source-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--bg-surface-muted) 78%, var(--accent-soft));
}

.source-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.source-doc-info {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 13px;
}

.source-doc-info svg {
  flex-shrink: 0;
  color: var(--accent);
}

.source-doc-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
  font-weight: 550;
}

.source-section {
  flex-shrink: 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.source-score {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 650;
}

.source-body {
  padding-top: 8px;
  border-top: 1px solid var(--border-subtle);
}

.source-content {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.source-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.source-open-btn {
  height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 28%, var(--border-subtle));
  border-radius: 9px;
  background: color-mix(in srgb, var(--accent-soft) 55%, var(--bg-surface));
  color: var(--accent);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition:
    background var(--transition-fast),
    color var(--transition-fast),
    border-color var(--transition-fast),
    transform var(--transition-fast),
    box-shadow var(--transition-fast);
}

.source-open-btn svg {
  flex-shrink: 0;
}

.source-open-btn:hover {
  border-color: color-mix(in srgb, var(--accent) 52%, var(--border-subtle));
  background: var(--accent);
  color: #fff;
  box-shadow: var(--shadow-xs);
  transform: translateY(-1px);
}

.source-open-btn:active {
  transform: translateY(0);
}

.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.2s ease;
  max-height: 260px;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
