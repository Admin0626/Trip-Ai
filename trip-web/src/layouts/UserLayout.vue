<script setup lang="ts">
import AppHeader from '@/components/common/AppHeader.vue'
import AppFooter from '@/components/common/AppFooter.vue'
</script>

<template>
  <div class="user-layout">
    <AppHeader />
    <main class="user-layout__main">
      <router-view />
    </main>
    <AppFooter />
  </div>
</template>

<style scoped lang="scss">
.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;

  &__main {
    flex: 1;
  }
}
</style>