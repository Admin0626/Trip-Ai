<script setup lang="ts">
import {computed,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import Recommend from './Recommend.vue'
import AiPlanner from '../plan/AiPlanner.vue'
const route=useRoute(),router=useRouter(),sharedQuery=ref(''),busy=ref(false)
const mode=computed(()=>route.query.mode==='routes'?'routes':route.query.mode==='ai'||route.path==='/ai-planner'?'ai':route.path==='/recommend'?'routes':'ai')
function choose(value:'routes'|'ai'){if(!busy.value&&value!==mode.value)void router.push({path:'/travel-assistant',query:{mode:value}})}
</script>
<template>
  <main class="travel-assistant page-wrapper" data-testid="travel-assistant">
    <header><h1>旅行助手</h1><p>描述旅行需求，选择查找已有路线，或连接自己的模型生成个性化行程。</p></header>
    <div class="assistant-modes" role="tablist" aria-label="旅行助手方式">
      <button id="assistant-ai-tab" type="button" role="tab" :aria-selected="mode==='ai'" aria-controls="assistant-panel" :disabled="busy" @click="choose('ai')">AI定制行程</button>
      <button id="assistant-routes-tab" type="button" role="tab" :aria-selected="mode==='routes'" aria-controls="assistant-panel" :disabled="busy" @click="choose('routes')">查找已有路线</button>
    </div>
    <p class="mode-note">{{mode === 'ai' ? '模型回答旅行建议，并生成可保存的每日行程。' : '筛选系统已发布路线，查看详情与预约，无需密钥。'}}两种方式沿用需求文字；已有路线按人均参考价筛选，AI按总预算规划。切换后需重新填写密钥。</p>
    <section v-page-enter="mode" id="assistant-panel" role="tabpanel" :aria-labelledby="mode==='ai'?'assistant-ai-tab':'assistant-routes-tab'">
      <AiPlanner v-if="mode==='ai'" embedded v-model:shared-query="sharedQuery" @busy="busy=$event" />
      <Recommend v-else embedded v-model:shared-query="sharedQuery" />
    </section>
  </main>
</template>
<style scoped>
h1 { font-size: 32px; font-weight: 600; margin: 0 0 24px; }
header p,.mode-note { color: var(--trip-muted); line-height: 1.7; }
.assistant-modes { display: flex; flex-wrap: wrap; gap: 16px; margin: 32px 0 16px; }
.assistant-modes button { border: 1px solid var(--trip-border); background: white; border-radius: 12px; padding: 12px 24px; min-height: 48px; font: inherit; cursor: pointer; color: var(--trip-forest); }
.assistant-modes button[aria-selected=true] { border-color: var(--trip-forest); background: var(--trip-forest); color: white; }
.assistant-modes button:disabled { cursor: wait; opacity: .65; }
.mode-note { font-size: 14px; margin-bottom: 32px; max-width: 80ch; }
@media (max-width:767px) { .assistant-modes { flex-direction: column; align-items: flex-start; } }
</style>
