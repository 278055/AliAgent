<template>
  <aside :class="['sidebar', { open: store.state.sidebarOpen, collapsed: store.state.sidebarCollapsed }]">
    <div class="sidebar-content">
      <div class="sidebar-header">
        <div class="sidebar-logo">
          <img src="@/assets/icons/aliagent-icon.png" alt="AliAgent" />
        </div>
        <button class="desktop-collapse-btn" :title="store.state.sidebarCollapsed ? '展开侧栏' : '收起侧栏'" @click="toggleSidebarCollapsed">
          <svg v-if="store.state.sidebarCollapsed" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="16" rx="2"/>
            <path d="M9 4v16"/>
            <path d="M13 9l3 3-3 3"/>
          </svg>
          <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="16" rx="2"/>
            <path d="M9 4v16"/>
            <path d="M16 9l-3 3 3 3"/>
          </svg>
        </button>
        <button class="mobile-close-btn" title="收起侧栏" @click="store.state.sidebarOpen = false">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="16" rx="2"/>
            <path d="M9 4v16"/>
          </svg>
        </button>
      </div>

      <nav class="sidebar-nav">
        <button class="nav-btn" @click="handleNewChat" title="新对话">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          <span>新对话</span>
        </button>
        <button class="nav-btn" @click="store.addToast('搜索功能即将上线', 'info')" title="搜索聊天">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.3-4.3"/>
          </svg>
          <span>搜索聊天</span>
        </button>
        <button :class="['nav-btn', { active: store.state.knowledgeBaseOpen }]" @click="toggleKnowledgeBase" title="知识库">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/>
          </svg>
          <span>知识库</span>
        </button>
      </nav>

      <section class="conversation-section">
        <div class="section-title">最近</div>
        <div class="conversation-list">
          <div
            v-for="conv in sortedConversations"
            :key="conv.id"
            :class="['conv-item', { active: conv.id === store.state.currentConversationId }]"
            @click="store.selectConversation(conv.id)"
            @contextmenu.prevent="openContextMenu($event, conv)"
          >
            <span v-if="conv.pinned" class="pin-dot" title="已置顶"></span>
            <span class="conv-title">{{ conv.title }}</span>
            <span class="conv-time">{{ formatTime(conv.updatedAt) }}</span>
          </div>
          <div v-if="store.state.conversations.length === 0" class="empty-list">
            <p>暂无对话</p>
            <p>点击新对话开始</p>
          </div>
        </div>
      </section>

      <div class="sidebar-footer">
        <div class="user-row">
          <div class="user-avatar">
            <img src="@/assets/icons/user-avatar.png" alt="用户头像" />
          </div>
          <div class="user-info">
            <span>当前用户</span>
            <small>{{ store.state.serviceOnline ? '服务在线' : '服务离线' }}</small>
          </div>
          <span :class="['status-dot', { online: store.state.serviceOnline }]"></span>
        </div>
        <div class="footer-actions">
          <button class="footer-btn" @click="toggleTheme" title="切换主题">
            <svg v-if="isDark" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="5"/>
              <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
            </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/>
            </svg>
          </button>
          <button class="footer-btn" @click="showLogoutConfirm = true" title="退出登录">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <ContextMenu
      v-if="contextMenu.visible"
      :x="contextMenu.x"
      :y="contextMenu.y"
      :conversation="contextMenu.conversation"
      @close="contextMenu.visible = false"
    />

    <LogoutConfirmModal
      v-if="showLogoutConfirm"
      @close="showLogoutConfirm = false"
      @confirm="handleLogout"
    />

    <div v-if="store.state.sidebarOpen" class="sidebar-overlay" @click="store.state.sidebarOpen = false"></div>
  </aside>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { store } from '@/stores'
import { formatRelativeTime } from '@/utils/time'
import { api } from '@/utils/api'
import ContextMenu from '@/components/ContextMenu.vue'
import LogoutConfirmModal from '@/components/LogoutConfirmModal.vue'

const isDark = computed(() => store.state.theme === 'dark')
const showLogoutConfirm = ref(false)

const sortedConversations = computed(() => {
  return [...store.state.conversations].sort((a, b) => {
    if (a.pinned !== b.pinned) return (b.pinned ?? 0) - (a.pinned ?? 0)
    return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  })
})

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  conversation: null as any,
})

function openContextMenu(event: MouseEvent, conv: any) {
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.conversation = conv
  contextMenu.visible = true
}

function formatTime(dateStr: string) {
  return formatRelativeTime(dateStr)
}

function handleNewChat() {
  store.newConversation()
  if (window.innerWidth <= 768) {
    store.state.sidebarOpen = false
  }
}

function toggleSidebarCollapsed() {
  store.setSidebarCollapsed(!store.state.sidebarCollapsed)
}

function toggleKnowledgeBase() {
  store.state.knowledgeBaseOpen = !store.state.knowledgeBaseOpen
}

function toggleTheme() {
  store.setTheme(isDark.value ? 'light' : 'dark')
}

async function handleLogout() {
  showLogoutConfirm.value = false
  try {
    await api.logout()
  } catch { /* ignore */ }
  store.state.authenticated = false
  store.state.conversations = []
  store.state.messages = []
  store.state.currentConversationId = null
}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 30;
  overflow: hidden;
  transition: width var(--transition-smooth);
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
  overflow: hidden;
}

.sidebar-header {
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.sidebar-logo {
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  overflow: hidden;
  background: transparent;
}

.sidebar-logo img,
.user-avatar img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.desktop-collapse-btn,
.mobile-close-btn,
.footer-btn {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.desktop-collapse-btn:hover,
.mobile-close-btn:hover,
.footer-btn:hover {
  background: var(--bg-sidebar-hover);
  color: var(--text-primary);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 18px;
}

.nav-btn {
  width: 100%;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 8px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.nav-btn svg,
.footer-btn svg,
.desktop-collapse-btn svg,
.mobile-close-btn svg {
  flex-shrink: 0;
}

.nav-btn:hover,
.nav-btn.active {
  background: var(--bg-sidebar-hover);
}

.conversation-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.section-title {
  padding: 0 8px 8px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 650;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  padding-right: 2px;
}

.conv-item {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border-radius: 9px;
  color: var(--text-primary);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.conv-item:hover,
.conv-item.active {
  background: var(--bg-sidebar-hover);
}

.pin-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--accent);
  flex-shrink: 0;
}

.conv-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.conv-time {
  flex-shrink: 0;
  color: var(--text-tertiary);
  font-size: 11px;
}

.empty-list {
  padding: 22px 8px;
  color: var(--text-tertiary);
  font-size: 13px;
  line-height: 1.7;
}

.sidebar-footer {
  flex-shrink: 0;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.user-row {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 6px 2px;
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

.user-info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.25;
}

.user-info small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--text-tertiary);
}

.status-dot.online {
  background: var(--success);
}

.footer-actions {
  display: flex;
  gap: 4px;
  padding-top: 4px;
}

.mobile-close-btn {
  display: none;
}

.sidebar.collapsed .sidebar-content {
  align-items: center;
  padding: 12px 10px;
}

.sidebar.collapsed .sidebar-header {
  width: 100%;
  justify-content: center;
  margin-bottom: 14px;
}

.sidebar.collapsed .sidebar-logo {
  width: 34px;
  height: 34px;
}

.sidebar.collapsed .desktop-collapse-btn {
  position: absolute;
  top: 52px;
  left: 50%;
  transform: translateX(-50%);
}

.sidebar.collapsed .sidebar-nav {
  width: 100%;
  align-items: center;
  margin-top: 40px;
  margin-bottom: 14px;
}

.sidebar.collapsed .nav-btn {
  width: 40px;
  height: 40px;
  justify-content: center;
  padding: 0;
  gap: 0;
}

.sidebar.collapsed .nav-btn span,
.sidebar.collapsed .conversation-section,
.sidebar.collapsed .user-info,
.sidebar.collapsed .status-dot {
  display: none;
}

.sidebar.collapsed .sidebar-footer {
  width: 100%;
  margin-top: auto;
  padding-top: 12px;
}

.sidebar.collapsed .user-row {
  justify-content: center;
  padding: 0 0 8px;
}

.sidebar.collapsed .user-avatar {
  width: 34px;
  height: 34px;
}

.sidebar.collapsed .footer-actions {
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.sidebar-overlay {
  display: none;
}

@media (min-width: 769px) {
  .sidebar.collapsed .sidebar-overlay {
    display: none;
  }
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    width: var(--sidebar-width);
    left: calc(-1 * var(--sidebar-width));
    transition: left var(--transition-smooth);
    box-shadow: var(--shadow-md);
  }

  .sidebar.open {
    left: 0;
  }

  .desktop-collapse-btn {
    display: none;
  }

  .mobile-close-btn {
    display: grid;
  }

  .sidebar.collapsed {
    width: var(--sidebar-width);
  }

  .sidebar.collapsed .sidebar-content {
    align-items: stretch;
    padding: 12px;
  }

  .sidebar.collapsed .sidebar-header {
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .sidebar.collapsed .sidebar-logo {
    width: 31px;
    height: 31px;
  }

  .sidebar.collapsed .sidebar-nav {
    width: auto;
    align-items: stretch;
    margin-top: 0;
    margin-bottom: 18px;
  }

  .sidebar.collapsed .nav-btn {
    width: 100%;
    height: 38px;
    justify-content: flex-start;
    padding: 0 8px;
    gap: 11px;
  }

  .sidebar.collapsed .nav-btn span,
  .sidebar.collapsed .conversation-section,
  .sidebar.collapsed .user-info,
  .sidebar.collapsed .status-dot {
    display: initial;
  }

  .sidebar.collapsed .conversation-section {
    display: flex;
  }

  .sidebar.collapsed .user-info {
    display: flex;
  }

  .sidebar.collapsed .sidebar-footer {
    width: auto;
    margin-top: 12px;
  }

  .sidebar.collapsed .user-row {
    justify-content: flex-start;
    padding: 6px 2px;
  }

  .sidebar.collapsed .user-avatar {
    width: 30px;
    height: 30px;
  }

  .sidebar.collapsed .footer-actions {
    flex-direction: row;
    align-items: center;
    gap: 4px;
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.22);
    z-index: -1;
  }
}
</style>
