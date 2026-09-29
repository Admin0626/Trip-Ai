import type { PlannerPreview } from '@/api/modules/planner'

export interface PlannerEvent { requestId:string; data:Record<string,unknown> }
const stages=new Set(['CONNECTING','GENERATING','VALIDATING','RETRYING'])
export async function readPlannerStream(stream:ReadableStream<Uint8Array>,requestId:string,onEvent:(name:string,event:PlannerEvent)=>void):Promise<PlannerPreview|null> {
  const reader=stream.getReader(),decoder=new TextDecoder('utf-8',{fatal:true})
  let event='message',data:string[]=[],bytes=0,lineParts:string[]=[],pendingCR=false
  let result:PlannerPreview|null|undefined
  function frame() {
    if(!data.length){event='message';return}
    const name=event,raw=data.join('\n');event='message';data=[]
    let parsed:PlannerEvent
    try{parsed=JSON.parse(raw) as PlannerEvent}catch{throw new Error('生成进度格式不正确')}
    if(parsed.requestId!==requestId||!parsed.data||typeof parsed.data!=='object')throw new Error('生成进度与当前请求不匹配')
    const d=parsed.data
    if(name==='progress'&&(!stages.has(d.stage as string)||!Number.isInteger(d.attempt)||![1,2].includes(d.attempt as number)))throw new Error('生成阶段格式不正确')
    if(name==='done'){
      const p=d as unknown as PlannerPreview
      if(p.source!=='USER_MODEL'||typeof p.model!=='string'||typeof p.draft?.title!=='string'||!Array.isArray(p.draft.dayList)||!p.draft.dayList.length||p.draft.dayList.some(day=>!Array.isArray(day.items)||!day.items.length))throw new Error('完整行程格式不正确')
      result=p
    }else if(name==='cancelled'){result=null}
    else if(name==='error')throw Object.assign(new Error(typeof d.message==='string'?d.message:'生成失败，请重试'),{code:d.code})
    onEvent(name,parsed)
  }
  function line(value:string){
    if(value===''){frame();return}
    if(value.startsWith(':'))return
    const colon=value.indexOf(':'),field=colon<0?value:value.slice(0,colon)
    let content=colon<0?'':value.slice(colon+1);if(content.startsWith(' '))content=content.slice(1)
    if(field==='event')event=content
    else if(field==='data')data.push(content)
  }
  function lines(text:string){
    if(!text)return
    let cursor=pendingCR&&text.startsWith('\n')?1:0;pendingCR=false
    const endings=/\r\n|\r|\n/g;endings.lastIndex=cursor
    for(let match=endings.exec(text);match;match=endings.exec(text)){
      lineParts.push(text.slice(cursor,match.index));line(lineParts.join(''));lineParts=[]
      cursor=endings.lastIndex
      if(result!==undefined)return
      pendingCR=match[0]==='\r'&&cursor===text.length
    }
    if(cursor<text.length)lineParts.push(text.slice(cursor))
  }
  try{
    while(result===undefined){
      const chunk=await reader.read()
      if(chunk.done){lines(decoder.decode());break}
      bytes+=chunk.value.byteLength
      if(bytes>2097152)throw new Error('生成响应过大，请减少行程天数')
      lines(decoder.decode(chunk.value,{stream:true}))
    }
    if(result===undefined)throw new Error('生成连接已结束，但未收到完整结果，请重新操作或切换普通生成')
    return result
  }finally{await reader.cancel().catch(()=>{});reader.releaseLock()}
}
