<template>
  <AuthShell v-if="!store.state.authenticated" @auth-success="onAuthSuccess" />
  <ChatShell v-else />
  <ToastContainer />
</template>

<script setup lang="ts">
import { store } from '@/stores'
import AuthShell from '@/components/AuthShell.vue'
import ChatShell from '@/components/ChatShell.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import { onMounted } from 'vue'

function onAuthSuccess() {
  store.state.authenticated = true
  store.loadConversations()
  store.checkHealth()
}

onMounted(() => {
  if (store.state.authenticated) {
    store.loadConversations()
    store.checkHealth()
  }
})
</script>