<script setup lang="ts">
const props = defineProps<{
  total: number
  current: number
  size: number
}>()

const emit = defineEmits<{ (e: 'change', current: number): void }>()

function onPageChange(page: number): void {
  emit('change', page)
}
</script>

<template>
  <div class="pagination-wrap">
    <el-pagination
      background
      layout="prev, pager, next, total"
      :total="props.total"
      :current-page="props.current"
      :page-size="props.size"
      @current-change="onPageChange"
    />
  </div>
</template>

<style scoped lang="scss">
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}
</style>