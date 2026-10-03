<script setup lang="ts">
import {onBeforeUnmount,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import request from '@/api/request'
type Kind='DESTINATION'|'ATTRACTION'|'ROUTE'
interface Preview{type:Kind;total:number;valid:boolean;rows:{index:number;name:string;valid:boolean;errors:string[]}[]}
const kind=ref<Kind>('DESTINATION'),file=ref<File|null>(null),preview=ref<Preview|null>(null),result=ref<{total:number;ids:number[];replayed:boolean}|null>(null),busy=ref(false),ack=ref(false)
let revision=0,requestId=''
onBeforeUnmount(()=>revision++)
function select(e:Event){revision++;file.value=(e.target as HTMLInputElement).files?.[0]||null;preview.value=null;result.value=null;ack.value=false;requestId=''}
function downloadTemplate(){
 const base={name:'示例目的地（请修改）',province:'浙江省',city:'杭州市',longitude:120.15,latitude:30.25,coverImg:'https://example.com/cover.jpg',tags:['nature'],avgCost:100,status:0}
 const record=kind.value==='DESTINATION'?base:kind.value==='ATTRACTION'?{destinationId:1,name:'示例景点（请修改）',ticketPrice:0,durationMin:60,status:0}:{destinationId:1,title:'示例一日路线（请修改）',coverImg:'https://example.com/cover.jpg',days:1,price:100,status:0,dayList:[{title:'第一天',summary:'安排说明',items:[{title:'自由活动',attractionId:0,timePoint:'09:00',cost:0}]}]}
 const url=URL.createObjectURL(new Blob([JSON.stringify({version:1,type:kind.value,records:[record]},null,2)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download=`${kind.value.toLowerCase()}-template.json`;a.click();URL.revokeObjectURL(url)
}
function data(){const body=new FormData();body.append('file',file.value!);return body}
async function inspect(){
 if(busy.value)return
 if(!file.value||file.value.size===0||file.value.size>1048576){ElMessage.warning('请选择1MiB以内的JSON文件');return}
 const version=++revision;preview.value=null;result.value=null;ack.value=false;requestId='';busy.value=true
 try{const r=await request.post<ApiResponse<Preview>>('/admin/catalog/import/preview',data());if(version===revision){preview.value=r.data.data;requestId=crypto.randomUUID()}}catch{}finally{if(version===revision)busy.value=false}
}
async function commit(){
 if(busy.value||!preview.value?.valid||!ack.value||!requestId||result.value)return
 const version=revision
 try{await ElMessageBox.confirm(`将新增${preview.value.total}条下架内容，不覆盖已有记录。确定导入吗？`,'确认批量导入',{type:'warning'})}catch{return}
 if(version!==revision||busy.value||!preview.value?.valid||!ack.value||result.value)return
 busy.value=true
 try{const body=data();body.append('requestId',requestId);body.append('confirmation','确认导入');const r=await request.post<ApiResponse<{total:number;ids:number[];replayed:boolean}>>('/admin/catalog/import/commit',body,{timeout:60000});if(version===revision){result.value=r.data.data;ElMessage.success('导入完成，请在对应目录审核后上架')}}catch{}finally{if(version===revision)busy.value=false}
}
</script>
<template><main class="admin-page"><h1>内容批量导入</h1><p class="admin-help">支持目的地、景点、路线与每日行程的JSON模板，每批1至50条、最多1MiB。仅新增下架内容（status=0），不覆盖已有记录。先导入目的地，再使用实际目的地ID导入景点或路线。</p><section class="admin-panel"><div class="admin-tools"><el-select v-model="kind" aria-label="导入模板类型" :disabled="busy"><el-option label="目的地" value="DESTINATION"/><el-option label="景点" value="ATTRACTION"/><el-option label="路线及每日行程" value="ROUTE"/></el-select><el-button :disabled="busy" @click="downloadTemplate">下载JSON模板</el-button></div><p class="admin-note">示例图片和ID是占位值，需替换。标签沿用目录规则，单个标签不含逗号；行程关联景点必须属于该目的地且已上架，手工活动使用attractionId=0。</p><input type="file" accept=".json,application/json" aria-label="选择导入JSON文件" :disabled="busy" @change="select"/><el-button :loading="busy" :disabled="!file" @click="inspect">预览并校验</el-button><section v-if="preview" class="preview" data-testid="import-preview"><h2>预览：{{preview.type}}，{{preview.total}}条</h2><p>预览不会写入数据；任意一条错误都会阻止整批导入。提交时会重新校验，数据变化可能使预览失效。</p><el-table :data="preview.rows"><el-table-column prop="index" label="序号" width="70"/><el-table-column prop="name" label="名称" min-width="140"/><el-table-column label="校验" min-width="220"><template #default="s"><span v-if="s.row.valid">通过</span><span v-else class="errors">{{s.row.errors.join('；')}}</span></template></el-table-column></el-table><el-checkbox v-model="ack" :disabled="busy||!preview.valid||!!result" data-testid="import-ack">我已检查内容，确认新增下架记录</el-checkbox><div><el-button type="primary" :loading="busy" :disabled="!preview.valid||!ack||!!result" @click="commit">确认导入整批</el-button></div></section><el-alert v-if="result" type="success" :closable="false" :title="`已导入${result.total}条；记录ID：${result.ids.join('、')}`" data-testid="import-result"/></section></main></template>
<style scoped>input{max-width:100%;margin:12px 12px 12px 0}.preview{margin-top:24px}.preview p{color:var(--trip-muted);line-height:1.7}.errors{color:#b91c1c}.el-checkbox{margin-top:20px;height:auto}:deep(.el-checkbox__label){white-space:normal}.preview .el-button{margin:16px 0}.el-alert{margin-top:16px}</style>
