import request from '@/api/request'

export { travelTags as preferenceOptions } from '@/utils/travelTags'
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
  effectiveCriteria:{destinations:string[];days:number|null;budgetMin:number|null;budgetMax:number|null;preferenceTags:string[];avoid:string[]}
  savedPreferenceFields:string[]
}
export function parseIntent(query: string): Promise<IntentResponse> {
  return request.post<ApiResponse<IntentResponse>>('/ai/recommend/intent', { query }).then(r => r.data.data)
}
export function matchRoutes(intent: TravelIntent, options?:{budgetMin?:number|null;useSavedPreference?:boolean}): Promise<MatchResponse> {
  const {budget,...rest}=intent
  return request.post<ApiResponse<MatchResponse>>('/ai/recommend/match', { intent:{...rest,budgetMin:options?.budgetMin??null,budgetMax:budget??null}, topN: 5,useSavedPreference:options?.useSavedPreference??false }).then(r => r.data.data)
}
