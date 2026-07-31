<script setup lang="ts">
import type { MetricCardView } from './types'
defineProps<{ items: MetricCardView[] }>()
</script>
<template><section class="panel"><h2>核心指标</h2><p v-if="!items.length" class="state">暂无指标数据</p><article v-for="item in items" :key="item.name" class="metric-card"><p>{{ item.name }}</p><strong>{{ item.value }}</strong><span>{{ item.trend }}</span><small>{{ item.definition }}</small></article></section></template>
