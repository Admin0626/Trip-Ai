<template>
  <footer class="app-footer">
    <div class="app-footer__inner container">
      <span>© 2026 智游行程 · 基于 LLM 的旅行行程推荐系统</span>
      <span class="text-secondary">毕业设计项目</span>
    </div>
  </footer>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.app-footer {
  background: #fff;
  border-top: 1px solid $color-border;
  padding: 16px 0;
  margin-top: 40px;

  &__inner {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    color: $color-text-secondary;
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  width: 100%;
}
</style>