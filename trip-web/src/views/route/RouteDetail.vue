<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { routeDetailApi } from '@/api/modules/route'
import { toggleLikeApi, toggleFavoriteApi, createBookingApi, commentPageApi, createCommentApi, deleteCommentApi, toggleCommentLikeApi } from '@/api/modules/interaction'
import { useUserStore } from '@/store/user'
import { dateAfter, formatMoney, timePoint } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import CatalogCover from '@/components/common/CatalogCover.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const id = Number(route.params.id)
const detail = ref<RouteDetailVO | null>(null)
const loading = ref(true)
const activeDay = ref(0)

const bookingVisible = ref(false)
const submitting = ref(false)
const bookingForm = reactive({ travelDate: dateAfter(1), peopleNum: 2, contactName: '', contactPhone: '', remark: '' })

const comments = ref<CommentVO[]>([])
const commentTotal = ref(0)
const commentLoading = ref(false)
const commentForm = reactive({ score: 5, content: '' })
const commentSending = ref(false)
const sentiment = ref('')
const commentCurrent = ref(1)
const replyTo = ref<CommentVO | null>(null)

const totalCost = computed(() => {
  if (!detail.value) return 0
  return detail.value.dayList.reduce((sum, d) => sum + d.items.reduce((s, it) => s + (it.cost || 0), 0), 0)
})

const tags = computed(() => detail.value?.tags ?? [])

function requireLogin(): boolean {
  if (userStore.isLoggedIn) return true
  ElMessage.warning('请先登录')
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function onLike(): Promise<void> {
  if (!requireLogin() || !detail.value) return
  const res = await toggleLikeApi(detail.value.id)
  detail.value.liked = res.liked
  detail.value.likeCount = res.likeCount
}

async function onFavorite(): Promise<void> {
  if (!requireLogin() || !detail.value) return
  const res = await toggleFavoriteApi(detail.value.id)
  detail.value.favorited = res.favorited
  detail.value.favoriteCount = res.favoriteCount
}

async function onBooking(): Promise<void> {
  if (!requireLogin() || !detail.value) return
  bookingForm.contactName = userStore.userInfo?.nickname || userStore.userInfo?.username || ''
  bookingVisible.value = true
}

async function submitBooking(): Promise<void> {
  if (!detail.value) return
  submitting.value = true
  try {
    await createBookingApi({ routeId: detail.value.id, ...bookingForm })
    ElMessage.success('预约提交成功，等待确认')
    bookingVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function loadComments(): Promise<void> {
  commentLoading.value = true
  try {
    const page = await commentPageApi({ routeId: id, current: commentCurrent.value, size: 10, sentiment: sentiment.value || undefined })
    comments.value = page.records
    commentTotal.value = page.total
  } finally {
    commentLoading.value = false
  }
}

async function submitComment(): Promise<void> {
  if (!commentForm.content.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  commentSending.value = true
  try {
    await createCommentApi({ routeId: id, score: commentForm.score, content: commentForm.content, parentId: replyTo.value?.id })
    ElMessage.success('评论成功')
    commentForm.content = ''
    commentForm.score = 5
    replyTo.value = null
    commentCurrent.value = 1
    detail.value = await routeDetailApi(id)
    void loadComments()
  } finally {
    commentSending.value = false
  }
}

function onSentimentChange(): void {
  commentCurrent.value = 1
  void loadComments()
}

async function deleteComment(c: CommentVO): Promise<void> {
  const ok = await ElMessageBox.confirm('确定删除这条评论吗？', '删除评论').then(() => true).catch(() => false)
  if (!ok) return
  await deleteCommentApi(c.id)
  await loadComments()
  detail.value = await routeDetailApi(id)
}

async function likeComment(c: CommentVO): Promise<void> {
  if (!requireLogin()) return
  const result = await toggleCommentLikeApi(c.id)
  c.liked = result.liked
  c.likeCount = result.likeCount
}

onMounted(async () => {
  try {
    detail.value = await routeDetailApi(id)
    activeDay.value = 0
  } finally {
    loading.value = false
  }
  void loadComments()
})
</script>

<template>
  <main v-loading="loading" class="route-detail page-wrapper">
    <!-- 顶部大图 -->
    <div v-if="detail" class="hero">
      <div class="hero__info">
        <p class="hero__eyebrow">路线 / {{ detail.destination?.name }}</p>
        <h1 class="hero__title">{{ detail?.title }}</h1>
        <p v-if="detail?.subtitle" class="hero__subtitle">{{ detail.subtitle }}</p>
        <div class="hero__tags">
          <el-tag v-for="t in tags" :key="t" size="small" type="success" effect="plain">{{ t }}</el-tag>
        </div>
        <div class="hero__stats">
          <span>{{ detail?.destination?.name }}</span>
          <span>{{ detail?.days }} 天</span>
          <span>点赞 {{ detail?.likeCount }}</span>
          <span>收藏 {{ detail?.favoriteCount }}</span>
          <span>预约 {{ detail?.bookingCount }}</span>
          <span>浏览 {{ detail?.viewCount }}</span>
        </div>
      </div>
      <CatalogCover :src="detail.coverImg" :alt="detail.title" landscape />
    </div>

    <div class="route-content">
      <!-- 操作条 -->
      <div v-if="detail" class="action-bar card">
        <div class="action-bar__left">
          <span class="price">{{ formatMoney(detail.price) }}<small>/人 · 人均参考价</small></span>
          <el-rate :model-value="Math.round(detail.avgScore ?? 0)" disabled />
          <span class="text-secondary">{{ (detail.avgScore ?? 0).toFixed(1) }} 分 · {{ detail.commentCount }} 条评论</span>
        </div>
        <div class="action-bar__right">
          <el-button type="primary" @click="onBooking">立即预约</el-button>
          <el-button @click="router.push({ path: '/plan/create', query: { fromRoute: detail.id } })">一键生成规划</el-button>
          <el-button :type="detail.liked ? 'primary' : 'default'" plain @click="onLike">
            {{ detail.liked ? '已点赞' : '点赞' }}
          </el-button>
          <el-button :type="detail.favorited ? 'primary' : 'default'" plain @click="onFavorite">
            {{ detail.favorited ? '已收藏' : '收藏' }}
          </el-button>
        </div>
      </div>

      <!-- 行程时间轴 -->
      <div v-if="detail" class="card section-card">
        <h2 class="section-title">每日行程</h2>
        <el-tabs v-model="activeDay" class="day-tabs">
          <el-tab-pane v-for="(day, i) in detail.dayList" :key="day.id" :name="i">
            <template #label>
              <div class="day-tab">
                <div class="day-tab__title">{{ day.title || `第 ${day.dayIndex} 天` }}</div>
                <div v-if="day.summary" class="day-tab__summary">{{ day.summary }}</div>
              </div>
            </template>
            <el-timeline v-if="day.items.length">
              <el-timeline-item v-for="item in day.items" :key="item.id" :timestamp="timePoint(item.timePoint)">
                <div class="item-card">
                  <div class="item-card__title">{{ item.title }}</div>
                  <div v-if="item.activity" class="item-card__line">活动：{{ item.activity }}</div>
                  <div v-if="item.transport" class="item-card__line">交通：{{ item.transport }}</div>
                  <div v-if="item.hotel" class="item-card__line">住宿：{{ item.hotel }}</div>
                  <div v-if="item.meal" class="item-card__line">餐饮：{{ item.meal }}</div>
                  <div v-if="item.durationMin" class="item-card__line">时长：{{ item.durationMin }} 分钟</div>
                  <div v-if="item.cost" class="item-card__line money">费用：{{ formatMoney(item.cost) }}</div>
                  <el-tag v-if="item.tips" size="small" type="info" effect="plain">提示：{{ item.tips }}</el-tag>
                </div>
              </el-timeline-item>
            </el-timeline>
            <EmptyState v-else icon="🗺️" text="当天暂无行程" />
          </el-tab-pane>
        </el-tabs>
        <div class="cost-summary">
          预算合计：<span class="money">{{ formatMoney(totalCost) }}</span>
        </div>
      </div>

      <!-- 评论区 -->
      <div class="card section-card">
        <h2 class="section-title">评论（{{ commentTotal }}）</h2>
        <div class="comment-toolbar">
          <el-radio-group v-model="sentiment" size="small" @change="onSentimentChange">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="positive">好评</el-radio-button>
            <el-radio-button value="neutral">中评</el-radio-button>
            <el-radio-button value="negative">差评</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="userStore.isLoggedIn" class="comment-form">
          <el-tag v-if="replyTo" closable @close="replyTo = null">正在回复 {{ replyTo.userNickname }}：{{ replyTo.content }}</el-tag>
          <el-rate v-model="commentForm.score" />
          <el-input v-model="commentForm.content" type="textarea" :rows="3" placeholder="写下你的评价…" maxlength="500" show-word-limit />
          <div class="comment-form__actions">
            <el-button type="primary" :loading="commentSending" @click="submitComment">发表评论</el-button>
          </div>
        </div>

        <div v-loading="commentLoading">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <el-avatar :size="36" :src="c.userAvatar">{{ c.userNickname?.slice(0, 1) }}</el-avatar>
            <div class="comment-item__body">
              <div class="comment-item__head">
                <span class="comment-item__name">{{ c.userNickname || '用户' }}</span>
                <el-rate :model-value="c.score" disabled size="small" />
                <el-tag v-if="c.sentiment && c.sentiment !== 'unknown'" size="small" :type="c.sentiment === 'positive' ? 'success' : c.sentiment === 'negative' ? 'danger' : 'info'">
                  {{ c.sentiment === 'positive' ? '好评' : c.sentiment === 'negative' ? '差评' : '中评' }}
                </el-tag>
              </div>
              <p class="comment-item__content">{{ c.content }}</p>
              <span v-if="c.parentId" class="text-secondary">回复评论 #{{ c.parentId }}</span>
              <div v-if="c.images?.length"><el-image v-for="src in c.images" :key="src" :src="src" :preview-src-list="c.images" style="width: 80px; height: 80px; margin-right: 8px" /></div>
              <div class="comment-item__foot text-secondary">{{ c.createTime }}</div>
              <el-button text size="small" @click="likeComment(c)">{{ c.liked ? '取消赞' : '赞' }} {{ c.likeCount }}</el-button>
              <el-button v-if="!c.parentId && userStore.isLoggedIn" text size="small" @click="replyTo = c">回复</el-button>
              <el-button v-if="c.userId === userStore.userInfo?.id" text size="small" type="danger" @click="deleteComment(c)">删除评论</el-button>
            </div>
          </div>
          <EmptyState v-if="!commentLoading && !comments.length" icon="💬" text="暂无评论，来抢沙发" />
          <el-pagination v-if="commentTotal > 10" v-model:current-page="commentCurrent" :page-size="10" :total="commentTotal" layout="prev, pager, next" @current-change="loadComments" />
        </div>
      </div>
    </div>

    <!-- 预约弹窗 -->
    <el-dialog v-model="bookingVisible" title="提交预约" width="480px">
      <el-form :model="bookingForm" label-width="90px">
        <el-form-item label="预约日期">
          <el-date-picker v-model="bookingForm.travelDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d: Date) => d.getTime() < Date.now()" style="width: 100%" />
        </el-form-item>
        <el-form-item label="出行人数">
          <el-input-number v-model="bookingForm.peopleNum" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="bookingForm.contactName" placeholder="联系人姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="bookingForm.contactPhone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bookingForm.remark" type="textarea" :rows="2" placeholder="特殊需求（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bookingVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBooking">提交预约</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.route-detail {
  .hero {
    display: grid;
    grid-template-columns: .925fr 1fr;
    gap: 48px;
    align-items: start;
    margin-bottom: 48px;

    &__info {
      min-width: 0;
      color: $color-text;
      display: grid;
      gap: 20px;
    }

    &__title {
      margin: 0;
      font-size: clamp(30px, 2.5vw, 36px);
      font-weight: 600;
      overflow-wrap: anywhere;
    }

    &__subtitle {
      margin: 0 0 8px;
      color: $color-text-secondary;
    }

    &__tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 10px;
    }

    &__stats {
      display: flex;
      gap: 16px;
      font-size: 14px;
      flex-wrap: wrap;
      color: $color-text-secondary;
    }
  }

  .action-bar {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 24px;
    margin-bottom: 48px;
    box-shadow: none;

    &__left {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;

      .price {
        font-size: 22px;
        color: $color-primary;
        font-weight: 700;

        small {
          font-size: 12px;
          color: $color-text-secondary;
          font-weight: 400;
        }
      }
    }
  }

  .section-card {
    padding: 24px;
    margin-bottom: 48px;
    box-shadow: none;
  }

  .section-title {
    margin: 0 0 12px;
    font-size: 24px;
  }

  .day-tab {
    text-align: center;

    &__title {
      font-weight: 600;
    }

    &__summary {
      font-size: 14px;
      color: $color-text-secondary;
      max-width: 120px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  .item-card {
    border: 1px solid $color-border;
    border-radius: $radius-md;
    padding: 12px;
    background: $color-bg;

    &__title {
      font-weight: 600;
      margin-bottom: 4px;
    }

    &__line {
      font-size: 14px;
      color: $color-text-secondary;
      margin-top: 2px;
    }
  }

  .cost-summary {
    margin-top: 16px;
    text-align: right;
    font-size: 14px;
    padding-top: 12px;
    border-top: 1px dashed $color-border;
  }

  .comment-toolbar {
    margin-bottom: 12px;
  }

  .comment-form {
    background: $color-bg;
    border-radius: $radius-md;
    padding: 12px;
    margin-bottom: 16px;

    &__actions {
      margin-top: 8px;
      text-align: right;
    }
  }

  .comment-item {
    display: flex;
    gap: 12px;
    padding: 12px 0;
    border-bottom: 1px solid $color-border;

    &:last-child {
      border-bottom: none;
    }

    &__body {
      flex: 1;
      min-width: 0;
      overflow-wrap: anywhere;
    }

    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    &__name {
      font-weight: 600;
      font-size: 14px;
    }

    &__content {
      margin: 6px 0;
      line-height: 1.6;
    }

    &__foot {
      font-size: 12px;
    }
  }
}
.hero__eyebrow { color: $color-primary; margin: 0; font-size: 14px; }
.action-bar__right { display: flex; flex-wrap: wrap; gap: 12px; }
.action-bar__right :deep(.el-button) { margin-left: 0; }
@media (max-width:767px) {
  .route-detail .hero { grid-template-columns: 1fr; gap: 32px; }
  .route-detail .hero__stats { gap: 12px; }
  .route-detail .action-bar__left { align-items: flex-start; }
  .route-detail .section-card { padding: 20px; }
  .route-detail :deep(.el-tabs__nav-wrap) { max-width: 100%; }
}
</style>
