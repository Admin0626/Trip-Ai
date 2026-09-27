<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { feedbackPage, createFeedback, type Feedback } from '@/api/modules/feedback'
import ImageUploader from '@/components/common/ImageUploader.vue'
const form=reactive({type:'FUNCTION',title:'',content:'',contact:'',images:[] as string[]})
const rows=ref<Feedback[]>([]),current=ref(1),total=ref(0),saving=ref(false),loading=ref(false)
const states=['待处理','处理中','已解决','已关闭']
async function load(){loading.value=true;try{const data=await feedbackPage(current.value);rows.value=data.records;total.value=data.total}finally{loading.value=false}}
async function submit(){if(!form.title.trim()||!form.content.trim()){ElMessage.warning('请填写标题和具体内容');return}saving.value=true;try{await createFeedback({...form});ElMessage.success('反馈已提交');form.title='';form.content='';form.images=[];current.value=1;await load()}finally{saving.value=false}}
onMounted(load)
</script>
<template><main class="feedback-page"><h1>意见与反馈</h1><p>告诉我们遇到的问题或改进建议，可在下方查看处理进度。</p><el-form class="card form" label-position="top" :disabled="saving">
<el-form-item label="反馈类型"><el-select v-model="form.type" aria-label="反馈类型"><el-option label="功能建议" value="FUNCTION"/><el-option label="使用问题" value="BUG"/><el-option label="内容纠错" value="CONTENT"/><el-option label="其他" value="OTHER"/></el-select></el-form-item>
<el-form-item label="反馈标题"><el-input v-model="form.title" maxlength="100" aria-label="反馈标题"/></el-form-item><el-form-item label="具体内容"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit aria-label="反馈内容"/></el-form-item><el-form-item label="联系方式（选填）"><el-input v-model="form.contact" maxlength="100" aria-label="反馈联系方式"/></el-form-item><el-form-item label="问题截图（最多3张）"><ImageUploader v-model="form.images"/></el-form-item><el-button type="primary" :loading="saving" @click="submit">提交反馈</el-button></el-form>
<h2>我的反馈</h2><section v-loading="loading"><el-empty v-if="!rows.length&&!loading" description="还没有反馈记录"/><article v-for="row in rows" :key="row.id" class="card record"><div class="heading"><h3>{{ row.title }}</h3><el-tag>{{ states[row.status] }}</el-tag></div><p class="text">{{ row.content }}</p><div class="images"><el-image v-for="url in row.images" :key="url" :src="url" :preview-src-list="row.images" fit="cover"/></div><p class="date">{{ row.createTime }}</p><div v-if="row.replyContent" class="reply"><strong>处理回复</strong><p class="text">{{ row.replyContent }}</p><small>{{ row.replyTime }}</small></div></article></section><el-pagination v-if="total>10" v-model:current-page="current" :total="total" :page-size="10" layout="prev,pager,next" @current-change="load"/></main></template>
<style scoped>.feedback-page{max-width:900px;margin:auto;padding:24px 16px}.form,.record{padding:24px;margin:20px 0}.heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.heading h3{margin:0}.text{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}.date,small{color:#64748b}.reply{background:#f1f5f9;padding:16px;border-radius:8px}.images{display:flex;gap:10px}.el-image{width:100px;height:80px}@media(max-width:640px){.form,.record{padding:16px}}</style>
