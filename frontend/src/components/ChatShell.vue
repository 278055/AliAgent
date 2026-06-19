<template>
  <div :class="['chat-shell', { 'sidebar-collapsed': store.state.sidebarCollapsed }]">
    <Sidebar />
    <ChatArea />
    <KnowledgeBase />
  </div>
</template>

<script setup lang="ts">
import Sidebar from '@/components/Sidebar.vue'
import ChatArea from '@/components/ChatArea.vue'
import KnowledgeBase from '@/components/KnowledgeBase.vue'
import { store } from '@/stores'
</script>

<style scoped>
.chat-shell {
  position: fixed;
  inset: 0;
  display: flex;
  background: var(--bg-page);
  overflow: hidden;
  --active-sidebar-width: var(--sidebar-width);
}

.chat-shell.sidebar-collapsed {
  --active-sidebar-width: 64px;
}
</style>
