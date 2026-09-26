<script setup lang="ts">
defineProps<{ value: number }>()
</script>

<template>
  <el-rate :model-value="value" disabled size="small" />
</template>