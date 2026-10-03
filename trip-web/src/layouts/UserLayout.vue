<script setup lang="ts">
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
</script>

<template>
  <div class="user-layout">
    <AppHeader />
    <div class="user-layout__main">
      <router-view />
    </div>
    <AppFooter />
  </div>
</template>

<style scoped lang="scss">
.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;

  &__main {
    flex: 1;
  }
}
</style>
