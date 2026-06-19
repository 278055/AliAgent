<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-panel animate-fade-in-up">
      <h3 class="modal-title">修改密码</h3>
      <form class="modal-form" @submit.prevent="handleSubmit">
        <div class="field">
          <label class="field-label">当前密码</label>
          <input v-model="form.oldPassword" type="password" class="input-field-full" required />
        </div>
        <div class="field">
          <label class="field-label">新密码</label>
          <input v-model="form.newPassword" type="password" class="input-field-full" required minlength="6" />
        </div>
        <div class="field">
          <label class="field-label">确认新密码</label>
          <input v-model="form.confirmPassword" type="password" class="input-field-full" required minlength="6" />
        </div>
        <p v-if="error" class="error-text">{{ error }}</p>
        <div class="modal-actions">
          <button type="button" class="cancel-btn" @click="$emit('close')">取消</button>
          <button class="confirm-btn" :disabled="loading">
            <span v-if="loading" class="btn-spinner"></span>
            <span v-else>确认修改</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { api } from '@/utils/api'
import { store } from '@/stores'

const emit = defineEmits<{ close: [] }>()

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  if (form.newPassword.length < 6) {
    error.value = '新密码至少需要6位'
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    error.value = '两次密码输入不一致'
    return
  }
  if (form.oldPassword === form.newPassword) {
    error.value = '新密码不能与旧密码相同'
    return
  }
  loading.value = true
  try {
    await api.changePassword(form.oldPassword, form.newPassword)
    store.addToast('密码修改成功', 'success')
    emit('close')
  } catch (e: any) {
    error.value = e.message || '修改失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}

.modal-panel {
  width: 420px;
  max-width: 90vw;
  border-radius: 18px;
  padding: 2rem;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-md);
}

.modal-title {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 1.5rem;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.field-label {
  font-family: var(--font-sans);
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.input-field-full {
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

.input-field-full:focus {
  border-color: var(--input-focus-border);
  box-shadow: var(--input-focus-shadow);
}

.error-text {
  font-family: var(--font-sans);
  font-size: 0.85rem;
  color: var(--danger);
  text-align: center;
}

.modal-actions {
  display: flex;
  gap: 0.8rem;
  justify-content: flex-end;
  margin-top: 0.5rem;
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
  transition: all var(--transition-smooth);
}

.cancel-btn:hover {
  color: var(--text-primary);
  border-color: var(--border-strong);
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

.btn-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid var(--border-medium);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
