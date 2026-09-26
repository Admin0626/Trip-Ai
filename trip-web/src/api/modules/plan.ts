// 规划接口（api/03 · /plan/**）
import request from '@/api/request'

export function createPlanApi(dto: PlanSaveDTO): Promise<number> {
  return request.post<ApiResponse<number>>('/plan', dto).then((r) => r.data.data)
}

export function updatePlanApi(id: number, dto: PlanSaveDTO): Promise<void> {
  return request.put<ApiResponse<void>>(`/plan/${id}`, dto).then((r) => r.data.data)
}

export function myPlansApi(params: { current?: number; size?: number; status?: number }): Promise<PageResult<PlanVO>> {
  return request.get<ApiResponse<PageResult<PlanVO>>>('/plan/page', { params }).then((r) => r.data.data)
}

export function planDetailApi(id: number): Promise<PlanVO> {
  return request.get<ApiResponse<PlanVO>>(`/plan/${id}`).then((r) => r.data.data)
}

export function deletePlanApi(id: number): Promise<void> {
  return request.delete<ApiResponse<void>>(`/plan/${id}`).then((r) => r.data.data)
}

export function copyPlanApi(id: number): Promise<number> {
  return request.post<ApiResponse<number>>(`/plan/${id}/copy`).then((r) => r.data.data)
}

export function exportPlanApi(id: number): Promise<string> {
  return request.post<ApiResponse<string>>(`/plan/${id}/export`).then((r) => r.data.data)
}

export function planFromRouteApi(routeId: number): Promise<number> {
  return request.post<ApiResponse<number>>(`/plan/from-route/${routeId}`).then((r) => r.data.data)
}