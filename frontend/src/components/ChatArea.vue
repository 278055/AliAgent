<template>
  <div class="chat-area">
    <header class="chat-header">
      <button class="menu-btn" @click="handleMenuClick" :title="store.state.sidebarCollapsed ? '展开侧栏' : '打开侧栏'">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="4" width="18" height="16" rx="2"/>
          <path d="M9 4v16"/>
          <path d="M13 9l3 3-3 3"/>
        </svg>
      </button>

      <h1 class="chat-title">{{ currentTitle || 'AliAgent' }}</h1>

      <div class="header-actions">
        <button v-if="store.state.currentConversationId" class="action-btn" @click="store.newConversation()" title="新对话">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
        </button>

        <div class="user-menu-wrapper">
          <button class="user-btn" @click.stop="showUserMenu = !showUserMenu" title="用户菜单">
            <div class="user-avatar">
              <img src="@/assets/icons/user-avatar.png" alt="用户头像" />
            </div>
          </button>
          <div v-if="showUserMenu" class="user-dropdown" @click.stop>
            <button class="dropdown-btn" @click="showPasswordModal = true; showUserMenu = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0110 0v4"/>
              </svg>
              修改密码
            </button>
            <button class="dropdown-btn" @click="store.addToast('即将上线', 'info'); showUserMenu = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              个人中心
            </button>
            <div class="dropdown-divider"></div>
            <button class="dropdown-btn danger" @click="showLogoutConfirm = true; showUserMenu = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/>
              </svg>
              退出登录
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="messages-container" ref="messagesRef">
      <div class="messages-inner">
        <WelcomeScreen v-if="store.state.messages.length === 0 && !store.state.isLoading" />

        <MessageBubble
          v-for="(msg, idx) in store.state.messages"
          :key="msg.id"
          :message="msg"
          :is-streaming="msg.role === 'assistant' && store.state.isLoading && idx === store.state.messages.length - 1"
          :streaming-content="store.state.streamingContent"
          :animation-delay="idx * 0.04"
        />
      </div>
    </div>

    <InputArea />

    <PasswordModal v-if="showPasswordModal" @close="showPasswordModal = false" />
    <LogoutConfirmModal
      v-if="showLogoutConfirm"
      @close="showLogoutConfirm = false"
      @confirm="handleLogout"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue'
import { store } from '@/stores'
import { api } from '@/utils/api'
import WelcomeScreen from '@/components/WelcomeScreen.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import InputArea from '@/components/InputArea.vue'
import PasswordModal from '@/components/PasswordModal.vue'
import LogoutConfirmModal from '@/components/LogoutConfirmModal.vue'

const currentTitle = computed(() => store.currentConversation.value?.title)
const messagesRef = ref<HTMLDivElement>()
const showUserMenu = ref(false)
const showPasswordModal = ref(false)
const showLogoutConfirm = ref(false)

watch(
  () => [store.state.messages.length, store.state.streamingContent],
  () => {
    nextTick(() => {
      if (messagesRef.value) {
        messagesRef.value.scrollTop = messagesRef.value.scrollHeight
      }
    })
  },
)

document.addEventListener('click', () => {
  if (showUserMenu.value) showUserMenu.value = false
})

async function handleLogout() {
  showLogoutConfirm.value = false
  showUserMenu.value = false
  try {
    await api.logout()
  } catch { /* ignore */ }
  store.state.authenticated = false
}

function handleMenuClick() {
  if (window.innerWidth <= 768) {
    store.state.sidebarOpen = true
    return
  }
  store.setSidebarCollapsed(false)
}
</script>

<style scoped>
.chat-area {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  overflow: hidden;
}

.chat-header {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 24px;
  background: var(--bg-page);
  border-bottom: 1px solid transparent;
  z-index: 20;
}

.menu-btn,
.action-btn,
.user-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.menu-btn:hover,
.action-btn:hover,
.user-btn:hover {
  background: var(--bg-surface-muted);
  color: var(--text-primary);
}

@media (min-width: 769px) {
  .menu-btn {
    display: none;
  }

  :global(.sidebar-collapsed) .menu-btn {
    display: grid;
  }
}

.chat-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-menu-wrapper {
  position: relative;
}

.user-avatar {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  overflow: hidden;
  background: transparent;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 178px;
  padding: 6px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
  z-index: 200;
}

.dropdown-btn {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 10px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  font-size: 14px;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.dropdown-btn:hover {
  background: var(--bg-surface-muted);
}

.dropdown-btn.danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.dropdown-divider {
  height: 1px;
  background: var(--border-subtle);
  margin: 5px 4px;
}

.messages-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 24px 152px;
}

.messages-inner {
  width: min(760px, 100%);
  min-height: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

@media (max-width: 768px) {
  .chat-header {
    padding: 0 14px;
    border-bottom-color: var(--border-subtle);
  }

  .messages-container {
    padding: 18px 14px 146px;
  }
}
</style>
