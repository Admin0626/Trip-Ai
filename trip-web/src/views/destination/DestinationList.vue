<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { destinationPageApi } from '@/api/modules/destination'
import EmptyState from '@/components/common/EmptyState.vue'
import Pagination from '@/components/common/Pagination.vue'

const router = useRouter()
const list = ref<DestinationVO[]>([])
const total = ref(0)
const current = ref(1)
const loading = ref(false)
const keyword = ref('')

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await destinationPageApi({ current: current.value, size: 12, keyword: keyword.value || undefined })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  current.value = 1
  void load()
}

function onPageChange(page: number): void {
  current.value = page
  void load()
}

onMounted(load)
</script>

<template>
  <div class="container page-wrapper">
    <div class="toolbar card">
      <el-input v-model="keyword" placeholder="搜索目的地" clearable style="width: 280px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-button type="primary" @click="onSearch">搜索</el-button>
    </div>

    <div v-loading="loading">
      <el-row v-if="list.length" :gutter="16">
        <el-col v-for="d in list" :key="d.id" :xs="12" :sm="8" :md="6">
          <div class="dest-card card" role="link" tabindex="0" @click="router.push(`/destination/${d.id}`)" @keydown.enter="router.push(`/destination/${d.id}`)">
            <img :src="d.coverImg" :alt="d.name" loading="lazy" />
            <div class="dest-card__body">
              <div class="dest-card__name">{{ d.name }}</div>
              <div class="dest-card__meta text-secondary">{{ d.province }} · {{ d.city }} · {{ d.routeCount ?? 0 }} 条路线</div>
            </div>
          </div>
        </el-col>
      </el-row>
      <EmptyState v-else-if="!loading" icon="🏔️" text="暂无目的地数据" />
    </div>

    <Pagination v-if="total > 12" :total="total" :current="current" :size="12" @change="onPageChange" />
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 20px;
}

.dest-card {
  margin-bottom: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-4px);
  }

  img {
    width: 100%;
    height: 120px;
    object-fit: cover;
    display: block;
  }

  &__body {
    padding: 10px 12px;
  }

  &__name {
    font-weight: 600;
    margin-bottom: 4px;
  }

  &__meta {
    font-size: 12px;
  }
}
</style>
