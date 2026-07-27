<script setup lang="ts">
import { reactive } from 'vue'
import { EvaluationClient } from '@aliagent/api-client'
import type { EvaluationConsoleState } from './types'

const client = new EvaluationClient()
const state = reactive<EvaluationConsoleState>({ candidates: [], datasets: [], tasks: [], comparisons: [], failures: [], decisions: [], error: '', pending: '' })

async function load() {
  state.error = ''
  try { [state.candidates, state.datasets, state.tasks, state.comparisons, state.failures, state.decisions] = await Promise.all([client.candidates(), client.datasets(), client.tasks(), client.comparisons(), client.failureSamples(), client.gateDecisions()]) }
  catch (error) { state.error = error instanceof Error ? error.message : '评测数据加载失败' }
}
async function review(candidateId: string, action: 'ACCEPT' | 'REJECT') { state.pending = candidateId; try { await client.reviewCandidate(candidateId, action); await load() } finally { state.pending = '' } }
async function start(datasetVersion: string, targetVersion: string) { state.pending = datasetVersion; try { await client.startTask(datasetVersion, targetVersion); await load() } finally { state.pending = '' } }
load()
</script>
<template>
  <main class="evaluation-console">
    <header><h1>持续评测控制台</h1><button @click="load">刷新</button></header>
    <p v-if="state.error" class="error">{{ state.error }}</p>
    <section class="evaluation-grid">
      <article><h2>候选审核</h2><p v-for="item in state.candidates" :key="item.candidateId"><code>{{ item.candidateId }}</code> · {{ item.status }} · {{ item.riskLevel }}<button :disabled="!!state.pending" @click="review(item.candidateId, 'ACCEPT')">接受</button><button :disabled="!!state.pending" @click="review(item.candidateId, 'REJECT')">拒绝</button></p></article>
      <article><h2>评测集</h2><p v-for="item in state.datasets" :key="item.datasetId">{{ item.name }} · {{ item.version }} · {{ item.sampleCount }} 样本 · {{ item.state }}<button v-if="item.state === 'PUBLISHED'" :disabled="!!state.pending" @click="start(item.version, '待选择目标版本')">启动任务</button></p></article>
      <article><h2>任务启动</h2><p v-for="item in state.tasks" :key="item.taskId"><code>{{ item.taskId }}</code> · {{ item.datasetVersion }} → {{ item.targetVersion }} · {{ item.state }}</p></article>
      <article><h2>版本对比</h2><p v-for="item in state.comparisons" :key="item.comparisonId">{{ item.baselineVersion }} → {{ item.targetVersion }} · {{ item.status }} · {{ item.delta }}</p></article>
      <article><h2>失败样本</h2><p v-for="item in state.failures" :key="item.sampleId"><code>{{ item.sampleId }}</code> · {{ item.metric }} · {{ item.riskLevel }} · {{ item.disposition }}</p></article>
      <article><h2>门禁结果</h2><p v-for="item in state.decisions" :key="item.decisionId"><strong>{{ item.status }}</strong> · {{ item.targetVersion }} · {{ item.policyVersion }} · {{ item.reasons.join(', ') }}</p></article>
    </section>
  </main>
</template>
