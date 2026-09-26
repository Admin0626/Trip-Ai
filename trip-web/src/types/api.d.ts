// 全局接口类型（03-前端设计.md §6.2；与后端 VO 对齐）
// 本文件为全局声明：不带 export，所有 .ts/.vue 可直接使用类型名。

/** 后端统一响应 R<T>：{ code, message, data, timestamp } */
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页响应 */
interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

// ---------- 用户 / 认证 ----------
interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar?: string
  role: 'USER' | 'ADMIN'
  city?: string
}

interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userInfo: UserInfo
}

// ---------- 目的地 / 景点 ----------
interface DestinationVO {
  id: number
  name: string
  coverImg?: string
  province?: string
  city?: string
  intro?: string
  heat?: number
  tags?: string
  routeCount?: number
}

interface AttractionVO {
  id: number
  destinationId?: number
  name: string
  coverImg?: string
  intro?: string
  address?: string
  ticketPrice?: number
  openTime?: string
  durationMin?: number
  tags?: string
}

// ---------- 路线 ----------
interface RoutePageVO {
  id: number
  title: string
  subtitle?: string
  coverImg?: string
  destinationId?: number
  destinationName?: string
  days: number
  price: number
  difficulty?: number
  tags?: string[]
  likeCount: number
  favoriteCount: number
  bookingCount: number
  commentCount: number
  viewCount: number
  avgScore: number
  isTop: number
  liked?: boolean
  favorited?: boolean
}

interface RouteItemVO {
  id: number
  sortNo: number
  timePoint?: string
  attractionId?: number
  title: string
  activity?: string
  transport?: string
  hotel?: string
  meal?: string
  durationMin?: number
  cost?: number
  tips?: string
}

interface RouteDayVO {
  id: number
  dayIndex: number
  title?: string
  summary?: string
  items: RouteItemVO[]
}

interface RouteDetailVO extends RoutePageVO {
  destination?: { id: number; name: string }
  highlights?: string
  notice?: string
  quotaPerDay?: number
  dayList: RouteDayVO[]
}

// ---------- 互动 ----------
interface LikeResult {
  liked: boolean
  likeCount: number
}

interface FavoriteStatus {
  favorited: boolean
  favoriteCount: number
}

interface BookingVO {
  bookingId: number
  bookingNo: string
  routeId: number
  routeTitle?: string
  travelDate: string
  peopleNum: number
  contactName: string
  contactPhone?: string
  remark?: string
  status: number
  createTime: string
}

interface CommentVO {
  id: number
  routeId: number
  parentId: number
  userId?: number
  userNickname?: string
  userAvatar?: string
  score: number
  content: string
  images?: string[]
  sentiment?: string
  likeCount: number
  liked?: boolean
  replyCount?: number
  createTime: string
  replies?: CommentVO[]
}

// ---------- 规划 ----------
interface PlanItemDTO {
  sortNo?: number
  timePoint?: string
  attractionId?: number
  title: string
  activity?: string
  transport?: string
  hotel?: string
  meal?: string
  durationMin?: number
  cost?: number
  tips?: string
}

interface PlanDayDTO {
  dayIndex?: number
  title?: string
  summary?: string
  items: PlanItemDTO[]
}

interface PlanSaveDTO {
  title: string
  destinationIds: number[]
  startDate: string
  days: number
  budget: number
  peopleNum: number
  status: number
  dayList: PlanDayDTO[]
}

interface PlanItemVO {
  id: number
  sortNo: number
  timePoint?: string
  attractionId?: number
  title: string
  activity?: string
  transport?: string
  hotel?: string
  meal?: string
  durationMin?: number
  cost?: number
  tips?: string
}

interface PlanDayVO {
  id: number
  dayIndex: number
  title?: string
  summary?: string
  items: PlanItemVO[]
}

interface PlanVO {
  id: number
  title: string
  destinationIds: number[]
  startDate: string
  days: number
  budget: number
  peopleNum: number
  status: number
  sourceRouteId?: number
  dayCount?: number
  createTime: string
  updateTime: string
  dayList?: PlanDayVO[]
}
