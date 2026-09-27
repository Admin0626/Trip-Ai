import request from '@/api/request'
export interface Feedback { id:number;userId:number;type:string;title:string;content:string;images:string[];contact:string;status:number;replyContent?:string;replyTime?:string;createTime:string }
export const feedbackPage=(current=1)=>request.get<ApiResponse<PageResult<Feedback>>>('/feedback/my/page',{params:{current,size:10}}).then(r=>r.data.data)
export const createFeedback=(data:{type:string;title:string;content:string;contact:string;images:string[]})=>request.post<ApiResponse<number>>('/feedback',data).then(r=>r.data.data)
