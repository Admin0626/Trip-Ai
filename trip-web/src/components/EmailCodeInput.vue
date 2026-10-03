<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { EmailReceipt } from '@/api/modules/auth'
const props=defineProps<{ email: string; modelValue: string; disabled?: boolean; send: () => Promise<EmailReceipt> }>()
const emit=defineEmits<{ 'update:modelValue': [string] }>()
const sending=ref(false),until=ref(0),now=ref(Date.now()),notice=ref('')
const seconds=computed(()=>Math.max(0,Math.ceil((until.value-now.value)/1000)))
const timer=window.setInterval(()=>{now.value=Date.now()},500)
onUnmounted(()=>window.clearInterval(timer))
watch(()=>props.email,()=>{emit('update:modelValue','');notice.value=''})
async function sendCode(){
  if(sending.value||seconds.value||props.disabled)return
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(props.email.trim())){ElMessage.warning('请填写有效邮箱');return}
  const email=props.email
  sending.value=true
  try{
    const result=await props.send()
    now.value=Date.now();until.value=now.value+result.retryAfter*1000
    if(props.email===email){notice.value=result.message;emit('update:modelValue','')}
  }catch{}finally{sending.value=false}
}
</script>
<template>
  <el-form-item label="邮箱验证码">
    <div class="code-row">
      <el-input :model-value="modelValue" @update:model-value="emit('update:modelValue',$event)" maxlength="6" inputmode="numeric" autocomplete="one-time-code" aria-label="邮箱验证码" placeholder="6位数字" :disabled="disabled"/>
      <el-button :loading="sending" :disabled="disabled||seconds>0" @click="sendCode">{{seconds?`${seconds}秒后重发`:'发送验证码'}}</el-button>
    </div>
    <p v-if="notice" role="status" class="code-notice">{{notice}}</p>
  </el-form-item>
</template>
<style scoped>.code-row{display:flex;gap:8px;width:100%}.code-row .el-input{min-width:0}.code-notice{font-size:12px;color:var(--trip-muted);line-height:1.6;margin:8px 0 0}</style>
