import request from '@/api/request'
export type OperationKind='bookings'|'comments'|'users'|'logs'
export interface AdminUser { id:number; username:string; nickname:string; city:string; role:string; status:number; createTime:string; lastLoginTime:string }
export interface AiLog { id:number;userId:number;scene:string;model:string;costMs:number;success:number;isFallback:number;createTime:string }
export const adminBookings=(params:object)=>request.get<ApiResponse<PageResult<BookingVO>>>('/admin/interaction/booking/page',{params}).then(r=>r.data.data)
export const adminComments=(params:object)=>request.get<ApiResponse<PageResult<CommentVO&{status:number}>>>('/admin/interaction/comment/page',{params}).then(r=>r.data.data)
export const adminUsers=(params:object)=>request.get<ApiResponse<PageResult<AdminUser>>>('/admin/user/page',{params}).then(r=>r.data.data)
export const adminLogs=(params:object)=>request.get<ApiResponse<PageResult<AiLog>>>('/admin/ai/recommend/log',{params}).then(r=>r.data.data)
export const operationStatus=(kind:OperationKind,id:number,status:number)=>request.put(kind==='users'?`/admin/user/${id}/status`:`/admin/interaction/${kind==='bookings'?'booking':'comment'}/${id}/status`,{status})
