<script setup lang="ts">
defineProps<{ text?: string; icon?: string }>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">{{ icon || '📭' }}</div>
    <p class="empty-state__text">{{ text || '暂无数据' }}</p>
    <slot />
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  color: #9ca3af;

  &__icon {
    font-size: 44px;
    margin-bottom: 8px;
  }

  &__text {
    margin: 0;
    font-size: 14px;
  }
}
</style>