// 路线接口（api/02 · /route/** 游客可读）
import request from '@/api/request'

export interface RouteQuery {
  current?: number
  size?: number
  keyword?: string
  destinationId?: number
  days?: number
  priceMin?: number
  priceMax?: number
  difficulty?: number
  tag?: string
  sortBy?: string
}

export function routePageApi(params: RouteQuery): Promise<PageResult<RoutePageVO>> {
  return request
    .get<ApiResponse<PageResult<RoutePageVO>>>('/route/page', { params })
    .then((r) => r.data.data)
}

export function routeDetailApi(id: number): Promise<RouteDetailVO> {
  return request.get<ApiResponse<RouteDetailVO>>(`/route/${id}`).then((r) => r.data.data)
}

export function routeHotApi(limit = 5): Promise<RoutePageVO[]> {
  return request.get<ApiResponse<RoutePageVO[]>>('/route/hot', { params: { limit } }).then((r) => r.data.data)
}

export function routeRecommendHomeApi(): Promise<RoutePageVO[]> {
  return request.get<ApiResponse<RoutePageVO[]>>('/route/recommend/home').then((r) => r.data.data)
}