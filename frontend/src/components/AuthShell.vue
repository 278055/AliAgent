<template>
  <div class="auth-shell">
    <button class="theme-toggle" @click="toggleTheme" title="切换主题">
      <svg v-if="isDark" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="5"/>
        <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
      </svg>
      <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/>
      </svg>
    </button>

    <main class="auth-main">
      <section class="auth-card">
        <div class="brand-block">
          <div class="brand-logo">A</div>
          <h1>AliAgent</h1>
          <p>简洁的 AI 知识库助手</p>
        </div>

        <div class="mode-switcher" role="tablist" aria-label="认证模式">
          <button
            :class="['mode-btn', { active: mode === 'login' }]"
            type="button"
            @click="mode = 'login'"
          >
            登录
          </button>
          <button
            :class="['mode-btn', { active: mode === 'register' }]"
            type="button"
            @click="mode = 'register'"
          >
            注册
          </button>
        </div>

        <form v-if="mode === 'login'" class="auth-form" @submit.prevent="handleLogin">
          <div class="field">
            <label class="field-label">用户名或邮箱</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <input
                v-model="loginForm.usernameOrEmail"
                type="text"
                class="input-field"
                placeholder="输入用户名或邮箱"
                required
                autocomplete="username"
              />
            </div>
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0110 0v4"/>
              </svg>
              <input
                v-model="loginForm.password"
                :type="showPassword ? 'text' : 'password'"
                class="input-field"
                placeholder="输入密码"
                required
                autocomplete="current-password"
              />
              <button class="password-toggle" @click="showPassword = !showPassword" type="button" title="显示或隐藏密码">
                <svg v-if="showPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.36 3.16"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
          </div>

          <button type="submit" class="submit-btn" :disabled="loginLoading">
            <span v-if="loginLoading" class="btn-spinner"></span>
            <span v-else>登录</span>
          </button>
          <p v-if="loginError" class="error-text">{{ loginError }}</p>
        </form>

        <form v-if="mode === 'register'" class="auth-form" @submit.prevent="handleRegister">
          <div class="field">
            <label class="field-label">用户名</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <input v-model="registerForm.username" type="text" class="input-field" placeholder="选择一个用户名" required autocomplete="username" />
            </div>
          </div>

          <div class="field">
            <label class="field-label">邮箱</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                <polyline points="22,6 12,13 2,6"/>
              </svg>
              <input v-model="registerForm.email" type="email" class="input-field" placeholder="输入邮箱地址" required autocomplete="email" />
            </div>
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0110 0v4"/>
              </svg>
              <input
                v-model="registerForm.password"
                :type="showPassword ? 'text' : 'password'"
                class="input-field"
                placeholder="设置密码（至少 6 位）"
                required
                minlength="6"
                autocomplete="new-password"
              />
              <button class="password-toggle" @click="showPassword = !showPassword" type="button" title="显示或隐藏密码">
                <svg v-if="showPassword" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.36 3.16"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
          </div>

          <button type="submit" class="submit-btn" :disabled="registerLoading">
            <span v-if="registerLoading" class="btn-spinner"></span>
            <span v-else>注册</span>
          </button>
          <p v-if="registerError" class="error-text">{{ registerError }}</p>
        </form>

        <p class="auth-footer">Powered by 通义千问 · DashScope</p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { api } from '@/utils/api'
import { store } from '@/stores'

const emit = defineEmits<{ authSuccess: [] }>()

const mode = ref<'login' | 'register'>('login')
const showPassword = ref(false)

const loginForm = ref({ usernameOrEmail: '', password: '' })
const registerForm = ref({ username: '', password: '', email: '' })

const loginLoading = ref(false)
const registerLoading = ref(false)
const loginError = ref('')
const registerError = ref('')

const isDark = computed(() => store.state.theme === 'dark')

function toggleTheme() {
  store.setTheme(isDark.value ? 'light' : 'dark')
}

async function handleLogin() {
  loginError.value = ''
  loginLoading.value = true
  try {
    await api.login(loginForm.value.usernameOrEmail, loginForm.value.password, true)
    store.state.authenticated = true
    emit('authSuccess')
  } catch (e: any) {
    loginError.value = e.message || '登录失败'
  } finally {
    loginLoading.value = false
  }
}

async function handleRegister() {
  registerError.value = ''
  if (registerForm.value.password.length < 6) {
    registerError.value = '密码至少需要6位'
    return
  }
  registerLoading.value = true
  try {
    await api.register(registerForm.value.username, registerForm.value.password, registerForm.value.email)
    await api.login(registerForm.value.username, registerForm.value.password, true)
    store.state.authenticated = true
    emit('authSuccess')
    store.addToast('注册成功', 'success')
  } catch (e: any) {
    registerError.value = e.message || '注册失败'
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.auth-shell {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--bg-page);
}

.auth-main {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at 50% 0%, color-mix(in srgb, var(--accent) 5%, transparent), transparent 38%),
    var(--bg-page);
}

.theme-toggle {
  position: fixed;
  top: 22px;
  right: 22px;
  z-index: 10;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  color: var(--text-secondary);
  cursor: pointer;
  box-shadow: var(--shadow-xs);
  transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast), box-shadow var(--transition-fast);
}

.theme-toggle:hover {
  color: var(--text-primary);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.auth-card {
  width: min(432px, calc(100vw - 32px));
  padding: 34px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 18px;
  box-shadow: var(--shadow-md);
  animation: fadeInUp 0.35s ease both;
}

.brand-block {
  text-align: center;
  margin-bottom: 26px;
}

.brand-logo {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: var(--text-primary);
  color: var(--bg-surface);
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.brand-block h1 {
  font-family: var(--font-display);
  font-size: 28px;
  line-height: 1.2;
  font-weight: 650;
  color: var(--text-primary);
  letter-spacing: 0;
}

.brand-block p {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.mode-switcher {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  padding: 4px;
  margin-bottom: 22px;
  background: var(--bg-surface-muted);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
}

.mode-btn {
  height: 38px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-weight: 550;
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast), box-shadow var(--transition-fast);
}

.mode-btn.active {
  background: var(--bg-surface);
  color: var(--text-primary);
  box-shadow: var(--shadow-xs);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field-label {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 550;
}

.input-wrapper {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--accent);
  box-shadow: var(--input-focus-shadow);
}

.input-icon {
  flex-shrink: 0;
  margin-right: 9px;
  color: var(--text-tertiary);
}

.input-field {
  min-width: 0;
  flex: 1;
  height: 42px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
}

.input-field::placeholder {
  color: var(--text-tertiary);
}

.password-toggle {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.password-toggle:hover {
  background: var(--bg-surface-muted);
  color: var(--text-primary);
}

.submit-btn {
  height: 44px;
  border: none;
  border-radius: 10px;
  background: var(--accent);
  color: white;
  font-weight: 650;
  cursor: pointer;
  transition: background var(--transition-fast), transform var(--transition-fast), opacity var(--transition-fast);
}

.submit-btn:hover:not(:disabled) {
  background: var(--accent-hover);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.btn-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.error-text {
  padding: 9px 10px;
  border-radius: 10px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
  text-align: center;
}

.auth-footer {
  margin-top: 22px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 520px) {
  .auth-main {
    padding: 18px;
  }

  .auth-card {
    padding: 26px 20px;
  }
}
</style>
