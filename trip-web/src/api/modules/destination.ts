// 目的地 / 景点接口
import request from '@/api/request'

/** 目的地列表（公开） */
export function destinationListApi(): Promise<DestinationVO[]> {
  return request.get<ApiResponse<DestinationVO[]>>('/destination/list').then((r) => r.data.data)
}

/** 目的地分页（含筛选） */
export function destinationPageApi(params: {
  current?: number
  size?: number
  keyword?: string
  province?: string
}): Promise<PageResult<DestinationVO>> {
  return request
    .get<ApiResponse<PageResult<DestinationVO>>>('/destination/page', { params })
    .then((r) => r.data.data)
}

/** 目的地详情 */
export function destinationDetailApi(id: number): Promise<DestinationVO> {
  return request.get<ApiResponse<DestinationVO>>(`/destination/${id}`).then((r) => r.data.data)
}

/** 热门目的地 */
export function destinationHotApi(limit = 8): Promise<DestinationVO[]> {
  return request
    .get<ApiResponse<DestinationVO[]>>('/destination/hot', { params: { limit } })
    .then((r) => r.data.data)
}

/** 景点列表（按目的地） */
export function attractionsApi(destinationId: number): Promise<AttractionVO[]> {
  return request
    .get<ApiResponse<AttractionVO[]>>(`/destination/${destinationId}/attractions`)
    .then((r) => r.data.data)
}