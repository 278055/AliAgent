<script setup lang="ts">
import { ref } from 'vue'
import EvaluationConsole from './evaluation/EvaluationConsole.vue'
import InsightConsole from './insight/InsightConsole.vue'
const page = ref<'insight' | 'evaluation'>('insight')
</script>
<template>
  <main><nav class="global-nav"><button @click="page = 'insight'">运营洞察</button><button @click="page = 'evaluation'">持续评测</button></nav><InsightConsole v-if="page === 'insight'" /><EvaluationConsole v-else /></main>
</template>
