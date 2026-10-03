<script setup lang="ts">
defineProps<{ text?: string; icon?: string }>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__art"><img src="/figma-landscape.svg" width="640" height="480" alt="" /></div>
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
  color: var(--trip-muted);
  gap: 16px;

  &__art {
    width: min(360px, 100%);
    aspect-ratio: 4 / 3;
    overflow: hidden;
    border-radius: 24px;
    img { display: block; width: 100%; height: auto; }
  }

  &__text {
    margin: 0;
    font-size: 16px;
    text-align: center;
  }
}
</style>
