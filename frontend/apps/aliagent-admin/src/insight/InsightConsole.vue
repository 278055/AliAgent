<script setup lang="ts">
import { reactive } from 'vue'
import ControlledQueryPanel from './ControlledQueryPanel.vue'
import MetricOverviewPanel from './MetricOverviewPanel.vue'
import ProblemRadarPanel from './ProblemRadarPanel.vue'
import TopicGapPanel from './TopicGapPanel.vue'
import { createInsightConsoleState } from './types'
import './insight.css'

const state = reactive(createInsightConsoleState())
state.radars = [{ id: 'radar-1', title: '退款率异常上升', status: 'OPEN', severity: '高', window: '近 24 小时' }]
state.metrics = [{ name: '退款率', value: '12.4%', trend: '+3.2%', definition: '订单归属周期 v1' }]
state.topics = [{ name: '物流延迟', summary: '多个已脱敏反馈提及配送延迟', risk: '普通', pendingReview: false, anonymized: true }, { name: '疑似欺诈', summary: '高风险主题，等待主管审核', risk: '欺诈', pendingReview: true, anonymized: true }]
</script>
<template>
  <main class="insight-console">
    <header><div><p class="eyebrow">运营洞察</p><h1>问题雷达与受控查询</h1></div><span class="trust-badge">仅已脱敏数据</span></header>
    <nav aria-label="洞察视图"><button v-for="view in ['radar', 'metrics', 'topics', 'query']" :key="view" :class="{ active: state.activeView === view }" @click="state.activeView = view as typeof state.activeView">{{ { radar: '问题雷达', metrics: '指标', topics: '主题与缺口', query: '自然语言查询' }[view] }}</button></nav>
    <p v-if="state.error" class="state error">{{ state.error }}</p>
    <ProblemRadarPanel v-if="state.activeView === 'radar'" :items="state.radars" :pending="state.pending" />
    <MetricOverviewPanel v-else-if="state.activeView === 'metrics'" :items="state.metrics" />
    <TopicGapPanel v-else-if="state.activeView === 'topics'" :items="state.topics" />
    <ControlledQueryPanel v-else v-model="state.queryPlan" :pending="state.pending" />
  </main>
</template>
