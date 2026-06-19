<template>
  <div class="logout-overlay" @click.self="$emit('close')">
    <div class="logout-panel animate-fade-in-up">
      <div class="logout-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/>
          <path d="M16 17l5-5-5-5"/>
          <path d="M21 12H9"/>
        </svg>
      </div>
      <h3 class="logout-title">确认退出登录？</h3>
      <p class="logout-desc">退出后需要重新登录才能继续使用 AliAgent。</p>
      <div class="logout-actions">
        <button class="cancel-btn" type="button" @click="$emit('close')">取消</button>
        <button class="confirm-btn" type="button" @click="$emit('confirm')">退出登录</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineEmits<{
  close: []
  confirm: []
}>()
</script>

<style scoped>
.logout-overlay {
  position: fixed;
  inset: 0;
  z-index: 420;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.22);
}

.logout-panel {
  width: min(380px, 100%);
  padding: 28px;
  border: 1px solid var(--border-subtle);
  border-radius: 18px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-md);
  text-align: center;
}

.logout-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  margin: 0 auto 16px;
  border-radius: 999px;
  background: var(--danger-soft);
  color: var(--danger);
}

.logout-title {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: 0;
}

.logout-desc {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.logout-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.cancel-btn,
.confirm-btn {
  height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  font-weight: 550;
  cursor: pointer;
  transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast);
}

.cancel-btn {
  border: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  color: var(--text-secondary);
}

.cancel-btn:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.confirm-btn {
  border: none;
  background: var(--danger);
  color: white;
}

.confirm-btn:hover {
  background: color-mix(in srgb, var(--danger) 88%, black);
}

@media (max-width: 460px) {
  .logout-panel {
    padding: 24px 20px;
  }

  .logout-actions {
    flex-direction: column-reverse;
  }

  .cancel-btn,
  .confirm-btn {
    width: 100%;
  }
}
</style>
