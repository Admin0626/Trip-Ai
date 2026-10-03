<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
const props=withDefaults(defineProps<{modelValue:string[];max?:number}>(),{max:3})
const emit=defineEmits<{ 'update:modelValue':[value:string[]] }>()
const uploading=ref(false)
async function select(event:Event){
  const input=event.target as HTMLInputElement, file=input.files?.[0]; if(!file)return
  if(!['image/png','image/jpeg'].includes(file.type)||file.size>5*1024*1024){ElMessage.warning('请选择5MB以内PNG/JPEG图片');input.value='';return}
  if(props.modelValue.length>=props.max){input.value='';return}
  uploading.value=true
  try{const body=new FormData();body.append('file',file);const response=await request.post<ApiResponse<{url:string}>>('/file/upload',body);emit('update:modelValue',[...props.modelValue,response.data.data.url])}
  catch{ /* Shared interceptor displays failure. */ }
  finally{uploading.value=false;input.value=''}
}
</script>
<template><div class="image-uploader"><div v-for="(url,i) in modelValue" :key="url" class="image"><img :src="url" alt="已上传图片"/><el-button size="small" :disabled="uploading" @click="emit('update:modelValue',modelValue.filter((_,index)=>index!==i))">移除图片</el-button></div><label v-if="modelValue.length<max">{{ uploading?'上传中…':'选择图片（PNG/JPEG，≤5MB）' }}<input type="file" accept="image/png,image/jpeg" :disabled="uploading" aria-label="上传图片" @change="select"/></label></div></template>
<style scoped>.image-uploader{display:flex;flex-wrap:wrap;gap:12px;align-items:center}.image{display:flex;flex-direction:column;gap:6px}.image img{width:100px;height:80px;object-fit:cover;border-radius:6px}label{display:flex;flex-direction:column;gap:6px;font-size:13px;color:var(--trip-muted)}input{max-width:240px}</style>
