<script setup lang="ts">
import type { TopicView } from './types'
defineProps<{ items: TopicView[] }>()
</script>
<template><section class="panel"><h2>主题与知识缺口</h2><p v-if="!items.length" class="state">暂无待分析主题</p><article v-for="item in items" :key="item.name" class="topic-card"><strong>{{ item.name }}</strong><span v-if="item.pendingReview" class="review">待主管审核</span><p>{{ item.summary }}</p><small v-if="item.anonymized">已脱敏样本</small></article></section></template>
