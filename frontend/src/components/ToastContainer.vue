<template>
  <div class="toast-container">
    <TransitionGroup name="toast">
      <div
        v-for="toast in store.toasts"
        :key="toast.id"
        :class="['toast-item', toast.type]"
        @click="store.removeToast(toast.id)"
      >
        <div class="toast-icon">
          <svg v-if="toast.type === 'success'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
          <svg v-if="toast.type === 'error'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-rose)" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
          <svg v-if="toast.type === 'info'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
          </svg>
        </div>
        <span class="toast-msg">{{ toast.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { store } from '@/stores'
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 500;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-width: 320px;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 13px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all var(--transition-smooth);
}

.toast-item.success {
  border-color: color-mix(in srgb, var(--success) 22%, var(--border-subtle));
}

.toast-item.error {
  border-color: color-mix(in srgb, var(--danger) 22%, var(--border-subtle));
}

.toast-item.info {
  border-color: color-mix(in srgb, var(--accent) 22%, var(--border-subtle));
}

.toast-icon {
  flex-shrink: 0;
}

.toast-msg {
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--text-primary);
}

/* ── Vue TransitionGroup ── */
.toast-enter-active {
  animation: fadeInUp 0.3s ease forwards;
}

.toast-leave-active {
  animation: fadeIn 0.2s ease reverse forwards;
}
</style>
