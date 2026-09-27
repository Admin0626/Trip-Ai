import request from '@/api/request'
export interface AdminFeedback { id:number;userId:number;type:string;title:string;content:string;contact:string;status:number;replyContent?:string;createTime:string }
export interface AdminSummary { users:number;destinations:number;routes:number;pendingBookings:number;pendingFeedback:number }
export const adminSummaryApi=()=>request.get<ApiResponse<AdminSummary>>('/admin/dashboard/summary').then(r=>r.data.data)
export const adminFeedbackApi=(current=1)=>request.get<ApiResponse<PageResult<AdminFeedback>>>('/admin/feedback/page',{params:{current,size:20}}).then(r=>r.data.data)
export const replyFeedbackApi=(id:number,data:{status:number;replyContent:string})=>request.put<ApiResponse<void>>(`/admin/feedback/${id}/reply`,data).then(r=>r.data.data)
