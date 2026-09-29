<script setup lang="ts">
import {onBeforeUnmount,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {publicKnowledge,type KnowledgeDoc} from '@/api/modules/knowledge'
const route=useRoute(),doc=ref<KnowledgeDoc|null>(null),error=ref(''),busy=ref(false);let version=0,mounted=true
async function load(){const v=++version;doc.value=null;error.value='';busy.value=true;const id=Number(route.params.id);try{if(!Number.isSafeInteger(id)||id<1)throw Error('资料编号不正确');const value=await publicKnowledge(id);if(mounted&&v===version)doc.value=value}catch(e){if(mounted&&v===version)error.value=e instanceof Error?e.message:'无法读取资料'}finally{if(v===version)busy.value=false}}
watch(()=>route.params.id,load,{immediate:true});onBeforeUnmount(()=>{mounted=false;version++})
</script>
<template><main class="knowledge-document" v-loading="busy"><router-link to="/knowledge">返回资料检索</router-link><el-alert v-if="error" :title="error" type="error" :closable="false"/><article v-if="doc" data-testid="knowledge-document"><h1>{{doc.title}}</h1><p class="hint">更新时间：{{new Date(doc.updateTime).toLocaleString('zh-CN')}} · 第{{doc.revision}}版</p><p class="hint">以下为资料原文，请核实其中的价格、开放时间与建议，不代表实时信息。</p><pre>{{doc.content}}</pre><router-link v-if="doc.sourceId" :to="(doc.docType==='ROUTE'?'/route/':'/destination/')+doc.sourceId">查看关联{{doc.docType==='ROUTE'?'路线':'目的地'}}</router-link></article></main></template>
<style scoped>.knowledge-document{max-width:960px;margin:24px auto;padding:24px;background:white;border-radius:14px;overflow-wrap:anywhere}.hint{color:#64748b;line-height:1.8}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.9}@media(max-width:640px){.knowledge-document{margin:16px 10px;padding:18px}}</style>
