<script setup lang="ts">
import type { ProblemRadarView } from './types'
defineProps<{ items: ProblemRadarView[]; pending: boolean }>()
</script>
<template><section class="panel"><h2>高对比问题雷达</h2><p v-if="pending" class="state">正在加载…</p><p v-else-if="!items.length" class="state">暂无开放问题</p><article v-for="item in items" :key="item.id" class="radar-card"><strong>{{ item.title }}</strong><span class="severity">{{ item.severity }}</span><p>{{ item.window }} · {{ item.status }}</p></article></section></template>
