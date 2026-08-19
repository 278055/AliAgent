<script setup lang="ts">
import { computed, ref } from 'vue'
import { canExecuteQuery, type QueryPlanView } from './types'
const plan = defineModel<QueryPlanView | undefined>({ required: true })
const props = defineProps<{ pending: boolean }>()
const question = ref('')
const ready = computed(() => canExecuteQuery({ activeView: 'query', radars: [], metrics: [], topics: [], queryPlan: plan.value, pending: props.pending, error: '' }))
function propose() { plan.value = { metric: question.value.trim() || '退款率', confirmed: false } }
function confirm() { if (plan.value) plan.value = { ...plan.value, confirmed: true } }
</script>
<template><section class="panel"><h2>自然语言查询</h2><label>问题<input v-model="question" placeholder="例如：近 7 天退款率按渠道变化" /></label><button @click="propose">生成受控计划</button><p v-if="plan" class="plan">计划：{{ plan.metric }} · <strong>{{ plan.confirmed ? '已确认' : '待确认' }}</strong></p><button v-if="plan && !plan.confirmed" @click="confirm">确认计划</button><button :disabled="!ready">执行已确认计划</button><p class="state">仅支持白名单指标和授权维度；不执行 SQL。</p></section></template>
