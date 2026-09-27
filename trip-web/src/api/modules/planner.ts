import request from '@/api/request'

export interface ModelConnection { baseUrl: string; model: string; apiKey: string }
export interface PlannerInput { connection: ModelConnection; query: string; days: number; budget: number; peopleNum: number; startDate: string }
export interface PlannerPreview { draft: { title: string; dayList: PlanDayDTO[] }; source: string; model: string; attempts: number }
export function plannerOptions() {
  return request.get<ApiResponse<{ allowedHosts: string[]; allowLoopback: boolean }>>('/ai/planner/options').then(r => r.data.data)
}
export function testModel(connection: ModelConnection) {
  return request.post<ApiResponse<{ connected: boolean; model: string }>>('/ai/planner/test', { connection }, { timeout: 65000 }).then(r => r.data.data)
}
export function generatePlan(input: PlannerInput) {
  return request.post<ApiResponse<PlannerPreview>>('/ai/planner/generate', input, { timeout: 125000 }).then(r => r.data.data)
}
