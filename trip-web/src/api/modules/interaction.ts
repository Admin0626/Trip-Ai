// 互动接口（api/03 · /interaction/**）
import request from '@/api/request'

// ----- 点赞 / 收藏 -----
export function toggleLikeApi(routeId: number): Promise<LikeResult> {
  return request.post<ApiResponse<LikeResult>>('/interaction/like', { routeId }).then((r) => r.data.data)
}

export function likeStatusApi(routeId: number): Promise<LikeResult> {
  return request.get<ApiResponse<LikeResult>>(`/interaction/like/${routeId}/status`).then((r) => r.data.data)
}

export function toggleFavoriteApi(routeId: number): Promise<FavoriteStatus> {
  return request.post<ApiResponse<FavoriteStatus>>('/interaction/favorite', { routeId }).then((r) => r.data.data)
}

// ----- 预约 -----
export interface BookingCreatePayload {
  routeId: number
  travelDate: string
  peopleNum: number
  contactName: string
  contactPhone?: string
  remark?: string
}

export function createBookingApi(payload: BookingCreatePayload): Promise<BookingVO> {
  return request.post<ApiResponse<BookingVO>>('/interaction/booking', payload).then((r) => r.data.data)
}

export function myBookingsApi(params: { current?: number; size?: number }): Promise<PageResult<BookingVO>> {
  return request
    .get<ApiResponse<PageResult<BookingVO>>>('/interaction/booking/page', { params })
    .then((r) => r.data.data)
}

export function cancelBookingApi(id: number): Promise<void> {
  return request.put<ApiResponse<void>>(`/interaction/booking/${id}/cancel`).then((r) => r.data.data)
}

// ----- 评论 -----
export interface CommentCreatePayload {
  routeId: number
  parentId?: number
  score: number
  content: string
  images?: string[]
}

export function createCommentApi(payload: CommentCreatePayload): Promise<CommentVO> {
  return request.post<ApiResponse<CommentVO>>('/interaction/comment', payload).then((r) => r.data.data)
}

export function commentPageApi(params: {
  routeId: number
  current?: number
  size?: number
  sortBy?: 'newest' | 'score'
  sentiment?: string
}): Promise<PageResult<CommentVO>> {
  return request
    .get<ApiResponse<PageResult<CommentVO>>>('/interaction/comment/page', { params })
    .then((r) => r.data.data)
}

export function deleteCommentApi(id: number): Promise<void> {
  return request.delete<ApiResponse<void>>(`/interaction/comment/${id}`).then((r) => r.data.data)
}

export function toggleCommentLikeApi(id: number): Promise<LikeResult> {
  return request.post<ApiResponse<LikeResult>>(`/interaction/comment/${id}/like`).then((r) => r.data.data)
}