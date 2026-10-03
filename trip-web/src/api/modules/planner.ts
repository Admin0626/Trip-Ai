import request from '@/api/request'
import { readPlannerStream,type PlannerEvent } from '@/utils/plannerStream'
import { getAuthSession } from '@/utils/storage'

export interface ModelConnection { baseUrl: string; model: string; apiKey: string }
export interface PlannerInput { connection: ModelConnection; query: string; days: number; budget: number; peopleNum: number; startDate: string }
export interface PlannerPreview { draft: { title: string; dayList: PlanDayDTO[];answer?:string|null }; source: string; model: string; attempts: number }
export interface QuotaBucket { limit: number; used: number; remaining: number; resetAt: string }
export interface UnlimitedDailyBucket { enabled: false; limit: null; used: null; remaining: null; resetAt: null }
export interface PlannerCircuit {
  phase: 'CLOSED' | 'OPEN' | 'HALF_OPEN'; total: number; failed: number; failurePercent: number
  minimumCalls: number; windowSeconds: number; cooldownSeconds: number; retryAt: string | null; retryAfterSeconds: number
}
export function plannerCircuit(connection: ModelConnection) {
  return request.post<ApiResponse<PlannerCircuit>>('/ai/planner/circuit', { connection }).then(r => r.data.data)
}
export interface PlannerUsage {
  quota: { hourly: QuotaBucket; daily: UnlimitedDailyBucket; globalDaily: UnlimitedDailyBucket; timeZone: string }
  today: { operations: number; succeeded: number; failed: number; averageCostMs: number }
}
export function plannerUsage() {
  return request.get<ApiResponse<PlannerUsage>>('/ai/planner/usage').then(r => r.data.data)
}
export function plannerOptions() {
  return request.get<ApiResponse<{ allowedHosts: string[]; allowLoopback: boolean }>>('/ai/planner/options').then(r => r.data.data)
}
export function testModel(connection: ModelConnection) {
  return request.post<ApiResponse<{ connected: boolean; model: string;reply:string }>>('/ai/planner/test', { connection }, { timeout: 65000 }).then(r => r.data.data)
}
export function generatePlan(input: PlannerInput) {
  return request.post<ApiResponse<PlannerPreview>>('/ai/planner/generate', input, { timeout: 125000 }).then(r => r.data.data)
}
export async function generatePlanStream(input:PlannerInput,requestId:string,signal:AbortSignal,onEvent:(name:string,event:PlannerEvent)=>void) {
  const epoch=getAuthSession()?.epoch
  const response=await request.post<ReadableStream<Uint8Array>>('/ai/planner/generate-stream',{requestId,input},
    {adapter:'fetch',responseType:'stream',signal,timeout:130000,headers:{Accept:'text/event-stream'}})
  if(!String(response.headers['content-type']).includes('text/event-stream')){
    const body=await new Response(response.data).json() as ApiResponse
    throw Object.assign(new Error(body.message||'无法启动生成进度'),{code:body.code})
  }
  return readPlannerStream(response.data,requestId,(name,event)=>{
    if(getAuthSession()?.epoch!==epoch)throw new Error('会话已变更，请重新操作')
    onEvent(name,event)
  })
}
export function cancelPlannerRequest(requestId:string) {
  return request.post<ApiResponse<{requestId:string;accepted:boolean;state:string}>>(`/ai/planner/requests/${requestId}/cancel`).then(r=>r.data.data)
}
