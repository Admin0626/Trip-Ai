import request from '@/api/request'

export interface AnalysisPoint { id:number; title:string; destinationId:number; destination:string; price:number; days:number; status:number; bookings:number }
export interface AnalysisDashboard {
  generatedAt:string; timezone:string; days:number; startDate:string; endDate:string; destinationId:number|null
  summary:{ users:number; newUsers:number; routes:number; bookings:number; cancelled:number; aiCalls:number; aiSucceeded:number; aiAverageMs:number|null }
  trend:{date:string; bookings:number; cancelled:number; newUsers:number; aiCalls:number}[]
  destinationHeat:{id:number; name:string; bookings:number}[]
  routeScatter:AnalysisPoint[]; matchingRoutes:number; scatterLimit:number
  bookingStatuses:{status:number;label:string;count:number}[]; aiStatuses:{status:number;label:string;count:number}[]
  destinations:{id:number;name:string}[]; destinationTotal:number; destinationLimit:number
}
export function analysisDashboard(days:number,destinationId:number|undefined,signal:AbortSignal) {
  return request.get<ApiResponse<AnalysisDashboard>>('/admin/analysis/dashboard',{params:{days,destinationId},signal}).then(r=>r.data.data)
}
