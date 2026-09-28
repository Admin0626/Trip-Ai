// A complete session is stored in one write so tabs cannot observe mixed token pairs.
export interface AuthSession { accessToken:string; refreshToken:string; userInfo:UserInfo; epoch:string }
const SESSION='trip_authSession'
const authListeners=new Set<()=>void>()
export function onAuthChange(listener:()=>void):()=>void {authListeners.add(listener);return()=>authListeners.delete(listener)}
function notifyAuth(){authListeners.forEach(listener=>listener())}
window.addEventListener('storage',event=>{if(event.key===null||[SESSION,'trip_token','trip_refreshToken','trip_userInfo'].includes(event.key))notifyAuth()})

export function getAuthSession():AuthSession|null {
  const raw=localStorage.getItem(SESSION)
  try {
    if(raw){const s=JSON.parse(raw) as AuthSession;return typeof s.accessToken==='string'&&typeof s.refreshToken==='string'&&typeof s.epoch==='string'&&s.userInfo?.id?s:null}
    const accessToken=localStorage.getItem('trip_token'),refreshToken=localStorage.getItem('trip_refreshToken'),userInfo=JSON.parse(localStorage.getItem('trip_userInfo')||'null') as UserInfo|null
    return accessToken&&refreshToken&&userInfo?.id?{accessToken,refreshToken,userInfo,epoch:'legacy'}:null
  }catch{return null}
}
function writeSession(session:AuthSession){
  localStorage.setItem(SESSION,JSON.stringify(session))
  // Legacy keys remain for migration/integrations; readers use the atomic entry.
  localStorage.setItem('trip_token',session.accessToken)
  localStorage.setItem('trip_refreshToken',session.refreshToken)
  localStorage.setItem('trip_userInfo',JSON.stringify(session.userInfo))
  notifyAuth()
}
export function setSession(data:LoginResult,expectedEpoch?:string):void {
  if(expectedEpoch!==undefined&&getAuthSession()?.epoch!==expectedEpoch)throw new Error('会话已变更，请重新操作')
  const newEpoch=()=>crypto.randomUUID?.()??`${Date.now()}-${Math.random()}-${Math.random()}`
  writeSession({accessToken:data.accessToken,refreshToken:data.refreshToken,userInfo:data.userInfo,epoch:expectedEpoch??newEpoch()})
}
export function getToken():string{return getAuthSession()?.accessToken||''}
export function getRefreshToken():string{return getAuthSession()?.refreshToken||''}
export function getUserInfo():Record<string,unknown>|null{return getAuthSession()?.userInfo as unknown as Record<string,unknown>||null}
export function setToken(token:string):void {const s=getAuthSession();if(s)writeSession({...s,accessToken:token});else{localStorage.setItem('trip_token',token);notifyAuth()}}
export function setUserInfo(info:unknown):void {
  const s=getAuthSession(),user=info as UserInfo
  if(s&&user?.id===s.userInfo.id)writeSession({...s,userInfo:user})
}
export function clearAuth():void {
  for(const key of ['trip_token','trip_refreshToken','trip_userInfo',SESSION])localStorage.removeItem(key)
  notifyAuth()
}
export function removeToken():void{clearAuth()}
export function removeUserInfo():void{clearAuth()}
