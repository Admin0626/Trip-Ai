<script setup lang="ts">
import {onBeforeUnmount,ref,watch} from 'vue'
import {ElMessageBox} from 'element-plus'
import {knowledgeHealth,repairKnowledge,type KnowledgeDoc,type KnowledgeHealth,type KnowledgeRepair} from '@/api/modules/knowledge'
const props=defineProps<{selected:KnowledgeDoc[];busy:boolean;refreshKey:number}>()
const emit=defineEmits<{ 'update:busy':[value:boolean];repaired:[] }>()
const health=ref<KnowledgeHealth|null>(null),loading=ref(false),error=ref(''),result=ref<KnowledgeRepair|null>(null)
let mounted=true,version=0
onBeforeUnmount(()=>{mounted=false;version++})
async function load(){const v=++version;loading.value=true;error.value='';try{const data=await knowledgeHealth();if(mounted&&v===version)health.value=data}catch(e){if(mounted&&v===version){health.value=null;error.value=e instanceof Error?e.message:'统计加载失败'}}finally{if(mounted&&v===version)loading.value=false}}
watch(()=>props.refreshKey,load,{immediate:true})
async function repair(){if(props.busy||props.selected.length<1||props.selected.length>10)return;const docs=props.selected.map(d=>({...d}));emit('update:busy',true);error.value='';try{
  await ElMessageBox.confirm(`修复已选${docs.length}篇资料的异常索引？每篇按当前版本独立处理，结果逐项显示。异常分片被替换后，旧历史引用可能失效，需要重新检索。`,'修复资料索引',{type:'warning',confirmButtonText:'确认修复',cancelButtonText:'取消'})
  if(!mounted)return;result.value=await repairKnowledge(docs);if(mounted){emit('repaired');await load()}
}catch(e){if(mounted&&e instanceof Error)error.value=e.message}finally{if(mounted)emit('update:busy',false)}}
</script>
<template>
  <section class="maintenance admin-panel" aria-label="知识索引维护">
    <div class="heading"><div><h2>索引健康与来源检查</h2><p class="admin-note">统计全部未删除资料；来源未公开的资料需要先检查目录，缺少关联ID的旧资料需人工选择来源。</p></div><el-button :loading="loading" :disabled="busy" @click="load">刷新健康统计</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <dl v-if="health" class="stats" data-testid="knowledge-health">
      <div><dt>资料总数</dt><dd>{{health.total}}</dd></div><div><dt>可公开检索</dt><dd>{{health.searchable}}</dd></div>
      <div><dt>索引就绪</dt><dd>{{health.indexReady}}</dd></div><div><dt>待修复索引</dt><dd data-testid="needs-rebuild">{{health.needsRebuild}}</dd></div>
      <div><dt>来源未公开</dt><dd>{{health.sourceUnavailable}}</dd></div><div><dt>待关联来源</dt><dd>{{health.unlinked}}</dd></div>
    </dl>
    <div class="repair-actions"><span>已选{{selected.length}}篇（每次最多10篇）</span><el-button type="primary" :disabled="busy||selected.length<1||selected.length>10" @click="repair">修复选中索引</el-button></div>
    <p class="admin-note">健康资料保留原分片；修复后沿用资料和来源的公开状态。版本冲突或失败需要刷新检查后再操作。</p>
    <div v-if="result" data-testid="repair-results" aria-live="polite"><p>已修复{{result.succeeded}}篇，保持就绪{{result.unchanged}}篇，失败{{result.failed}}篇。</p><ul class="results"><li v-for="r in result.results" :key="r.id"><strong>资料 #{{r.id}}</strong> · {{r.outcome==='REPAIRED'?'已修复':r.outcome==='UNCHANGED'?'保持就绪':'失败'}}（{{r.code}}）：{{r.message}}</li></ul></div>
  </section>
</template>
<style scoped>
.maintenance{margin-bottom:20px}.heading,.repair-actions{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap}h2{margin:0;font-size:20px}.stats{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px;margin:20px 0}.stats>div{padding:14px;background:#f8fafc;border-radius:8px}.stats dt{color:#64748b;font-size:13px}.stats dd{margin:8px 0 0;font-size:25px;font-weight:600}.results{padding-left:20px;line-height:1.9;overflow-wrap:anywhere}@media(max-width:1000px){.stats{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:600px){.stats{grid-template-columns:repeat(2,minmax(0,1fr))}.stats>div{padding:10px}}
</style>
