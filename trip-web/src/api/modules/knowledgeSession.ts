import request from '@/api/request'
import type {KnowledgeReference} from './knowledge'
export interface KnowledgeSession {id:number;title:string;mode:'LOCAL_SEARCH';revision:number;messageCount:number;lastMessageTime:string|null;createTime:string;updateTime:string}
export interface HistoryResult {query:string|null;mode:'LOCAL_NGRAM';matchedAtSearch:boolean;message:string;references:KnowledgeReference[];unavailableReferenceCount:number}
export interface HistoryMessage {id:number;role:string;messageType:string;content:string;requestId:string|null;tokensUsed:number;costMs:number;createTime:string;result:HistoryResult|null}
export interface HistoryPage {records:HistoryMessage[];hasMore:boolean;nextBeforeId:number|null}
export interface HistorySearch {sessionId:number;query:string;topK:number;requestId:string}
export interface HistoryTurn {session:KnowledgeSession;requestId:string;replayed:boolean;messages:HistoryMessage[]}
export function sessionPage(params:{current:number;size?:number;keyword?:string}) {return request.get<ApiResponse<PageResult<KnowledgeSession>>>('/ai/chat/session/page',{params}).then(r=>r.data.data)}
export function createSession(title?:string){return request.post<ApiResponse<KnowledgeSession>>('/ai/chat/session',{title}).then(r=>r.data.data)}
export function sessionDetail(id:number){return request.get<ApiResponse<KnowledgeSession>>('/ai/chat/session/'+id).then(r=>r.data.data)}
export function renameSession(session:KnowledgeSession,title:string){return request.put<ApiResponse<KnowledgeSession>>('/ai/chat/session/'+session.id,{title,expectedRevision:session.revision}).then(r=>r.data.data)}
export function deleteSession(session:KnowledgeSession){return request.delete('/ai/chat/session/'+session.id,{params:{expectedRevision:session.revision}})}
export function historyPage(sessionId:number,beforeId?:number){return request.get<ApiResponse<HistoryPage>>('/ai/chat/message/page',{params:{sessionId,size:20,beforeId}}).then(r=>r.data.data)}
export function searchInSession(input:HistorySearch){return request.post<ApiResponse<HistoryTurn>>('/ai/chat/search',input).then(r=>r.data.data)}
