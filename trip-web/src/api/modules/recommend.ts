import request from '@/api/request'

export const preferenceOptions = ['自然风光', '历史文化', '美食', '亲子', '摄影', '探险', '古城', '慢生活', '海边', '徒步', '温泉', '滑雪', '夜游', '露营', '民俗', '购物']
export interface TravelIntent {
  destinations: string[]
  days: number | null
  budget: number | null
  budgetLevel: string | null
  preferenceTags: string[]
  companions: string | null
  pace: string | null
  travelMonth: number | null
  mustVisit: string[]
  avoid: string[]
}
export interface IntentResponse { intent: TravelIntent; confidence: number; source: string }
export interface MatchedRoute {
  routeId: number; title: string; coverImg: string; destinationName: string
  days: number; price: number; tags: string[]; reason: string; highlightMatch: string[]
  recallScore: number; llmScore: number | null
}
export interface MatchResponse {
  totalCandidates: number; source: string; unsupportedCriteria: string[]; list: MatchedRoute[]
}
export function parseIntent(query: string): Promise<IntentResponse> {
  return request.post<ApiResponse<IntentResponse>>('/ai/recommend/intent', { query }).then(r => r.data.data)
}
export function matchRoutes(intent: TravelIntent): Promise<MatchResponse> {
  return request.post<ApiResponse<MatchResponse>>('/ai/recommend/match', { intent, topN: 5 }).then(r => r.data.data)
}
