<template>
  <div class="welcome-screen">
    <div class="welcome-content">
      <div class="welcome-logo">
        <img src="@/assets/icons/aliagent-icon.png" alt="AliAgent" />
      </div>
      <h2 class="welcome-title">AliAgent</h2>
      <p class="welcome-sub">今天想了解什么？</p>

      <div class="suggestions">
        <button class="suggestion-card" v-for="s in suggestions" :key="s.label" @click="store.sendMessage(s.prompt)">
          <span class="suggestion-label">{{ s.label }}</span>
          <span class="suggestion-prompt">{{ s.prompt }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { store } from '@/stores'

const suggestions = [
  { label: '知识问答', prompt: '解释一下量子计算的基本原理' },
  { label: '代码助手', prompt: '帮我写一个 Python 快速排序算法' },
  { label: '文案撰写', prompt: '帮我写一段产品发布的营销文案' },
  { label: '知识库检索', prompt: '根据知识库总结一下重点内容' },
]
</script>

<style scoped>
.welcome-screen {
  flex: 1;
  min-height: 420px;
  display: grid;
  place-items: center;
  padding: 32px 0;
}

.welcome-content {
  width: min(560px, 100%);
  text-align: center;
  animation: fadeInUp 0.32s ease both;
}

.welcome-logo {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  margin: 0 auto 16px;
  border-radius: 16px;
  overflow: hidden;
  background: transparent;
  box-shadow: var(--shadow-sm);
}

.welcome-logo img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.welcome-title {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0;
}

.welcome-sub {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 15px;
}

.suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 30px;
}

.suggestion-card {
  min-height: 70px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 4px;
  padding: 13px 14px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}

.suggestion-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.suggestion-label {
  font-size: 14px;
  font-weight: 650;
}

.suggestion-prompt {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-tertiary);
  font-size: 12px;
}

@media (max-width: 560px) {
  .suggestions {
    grid-template-columns: 1fr;
  }

  .welcome-title {
    font-size: 28px;
  }
}
</style>
