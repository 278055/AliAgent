import { createInsightConsoleState, canExecuteQuery } from './types'

const state = createInsightConsoleState()
if (state.activeView !== 'radar') throw new Error('默认视图必须为问题雷达')
if (canExecuteQuery(state)) throw new Error('未确认的查询计划不得执行')
state.queryPlan = { metric: '退款率', confirmed: true }
if (!canExecuteQuery(state)) throw new Error('确认后的查询计划应可执行')
