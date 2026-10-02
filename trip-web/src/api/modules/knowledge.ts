import request from '@/api/request'
export interface KnowledgeDoc { id:number;title:string;docType:'GUIDE'|'DESTINATION'|'ROUTE';sourceType:string;sourceId:number|null;status:number;revision:number;indexedRevision:number;indexMethod:string;chunkCount:number;vectorStatus:number;updateTime:string;content?:string;sourceAvailable?:boolean;actualChunkCount?:number;indexState?:'READY'|'NEEDS_REBUILD';indexIssue?:string;sourceState?:'AVAILABLE'|'UNAVAILABLE'|'UNLINKED' }
export interface KnowledgeHealth { total:number;enabled:number;disabled:number;indexReady:number;needsRebuild:number;sourceUnavailable:number;unlinked:number;searchable:number }
export interface KnowledgeRepair { total:number;succeeded:number;unchanged:number;failed:number;results:{id:number;code:number;outcome:'REPAIRED'|'UNCHANGED'|'FAILED';message:string;revision:number|null;chunkCount:number|null}[] }
export interface KnowledgeInput { title:string;docType:KnowledgeDoc['docType'];sourceId:number|null;content:string;status:number;expectedRevision?:number }
export interface KnowledgeReference { docId:number;chunkId:number;chunkIndex:number;title:string;excerpt:string;keywordCoverage:number;documentPath:string;sourcePath:string|null }
export interface KnowledgeResult { query:string;mode:'LOCAL_NGRAM';matched:boolean;message:string;references:KnowledgeReference[] }
export function knowledgePage(params:{current:number;keyword?:string;status?:number;indexState?:KnowledgeDoc['indexState'];sourceState?:KnowledgeDoc['sourceState']}) {return request.get<ApiResponse<PageResult<KnowledgeDoc>>>('/admin/ai/knowledge/page',{params}).then(r=>r.data.data)}
export function knowledgeHealth(){return request.get<ApiResponse<KnowledgeHealth>>('/admin/ai/knowledge/health').then(r=>r.data.data)}
export function repairKnowledge(docs:KnowledgeDoc[]){return request.post<ApiResponse<KnowledgeRepair>>('/admin/ai/knowledge/repair',{documents:docs.map(d=>({id:d.id,expectedRevision:d.revision}))}).then(r=>r.data.data)}
export function knowledgeDetail(id:number){return request.get<ApiResponse<KnowledgeDoc>>('/admin/ai/knowledge/'+id).then(r=>r.data.data)}
export function saveKnowledge(input:KnowledgeInput,id?:number){return (id?request.put<ApiResponse<KnowledgeDoc>>('/admin/ai/knowledge/'+id,input):request.post<ApiResponse<KnowledgeDoc>>('/admin/ai/knowledge',input)).then(r=>r.data.data)}
export function setKnowledgeState(doc:KnowledgeDoc,status:number){return request.put('/admin/ai/knowledge/'+doc.id+'/status',{expectedRevision:doc.revision,status})}
export function deleteKnowledge(doc:KnowledgeDoc){return request.delete('/admin/ai/knowledge/'+doc.id,{params:{expectedRevision:doc.revision}})}
export function rebuildKnowledge(doc:KnowledgeDoc){return request.post('/admin/ai/knowledge/rebuild',{id:doc.id,expectedRevision:doc.revision})}
export function uploadKnowledge(input:KnowledgeInput,file:File){const body=new FormData();body.append('title',input.title);body.append('docType',input.docType);if(input.sourceId!==null)body.append('sourceId',String(input.sourceId));body.append('status',String(input.status));body.append('file',file);return request.post<ApiResponse<KnowledgeDoc>>('/admin/ai/knowledge/upload',body).then(r=>r.data.data)}
export function searchKnowledge(query:string,topK=5){return request.post<ApiResponse<KnowledgeResult>>('/ai/knowledge/search',{query,topK}).then(r=>r.data.data)}
export function publicKnowledge(id:number){return request.get<ApiResponse<KnowledgeDoc>>('/ai/knowledge/documents/'+id).then(r=>r.data.data)}
