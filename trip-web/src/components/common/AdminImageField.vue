<script setup lang="ts">
import { computed } from 'vue'
import ImageUploader from './ImageUploader.vue'
const props=defineProps<{modelValue:string;label?:string}>()
const emit=defineEmits<{ 'update:modelValue':[value:string] }>()
const images=computed({get:()=>props.modelValue?[props.modelValue]:[],set:(v:string[])=>emit('update:modelValue',v[0]||'')})
</script>
<template><div><el-input :model-value="modelValue" :aria-label="label||'封面图片地址'" placeholder="填写图片地址或上传图片" maxlength="255" @update:model-value="emit('update:modelValue',$event)"/><ImageUploader v-model="images" :max="1"/></div></template>
