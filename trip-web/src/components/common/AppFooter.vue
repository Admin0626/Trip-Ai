<template>
  <footer class="app-footer">
    <div class="app-footer__inner container">
      <span>Trip-AI · 智游行程</span>
      <span class="text-secondary">发现目的地，安排下一段旅程</span>
    </div>
  </footer>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.app-footer {
  background: #fff;
  border-top: 1px solid $color-border;
  padding: 24px 0;
  margin-top: 40px;

  &__inner {
    display: flex;
    flex-direction: column;
    gap: 16px;
    font-size: 14px;
    color: $color-text-secondary;
  }
}

.container {
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 20px;
  width: 100%;
}
</style>
