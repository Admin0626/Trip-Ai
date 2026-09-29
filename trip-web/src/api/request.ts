import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getAuthSession, setSession, clearAuth } from '@/utils/storage'
import router from '@/router'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
const request: AxiosInstance = axios.create({ baseURL, timeout: 30000 })
// Refresh uses its own client so a failed refresh cannot recurse.
const refreshClient = axios.create({ baseURL, timeout: 15000 })
type AuthConfig = InternalAxiosRequestConfig & { _retried?: boolean; _authToken?: string; _authUserId?: number; _authEpoch?:string }
let expiredNotice = false
const publicAuth = (url = '') => ['/auth/login', '/auth/register', '/auth/refresh'].includes(url)

request.interceptors.request.use((config: AuthConfig) => {
  const session=getAuthSession(),token=session?.accessToken
  if(config._retried&&(config._authEpoch!==session?.epoch||config._authUserId!==session?.userInfo.id))throw new Error('会话已变更，请重新操作')
  if (token && !publicAuth(config.url)) config.headers.Authorization = `Bearer ${token}`
  else delete config.headers.Authorization
  config._authToken = token
  config._authUserId = session?.userInfo.id
  config._authEpoch = session?.epoch
  return config
})

function handleUnauthorized(message='登录已过期，请重新登录'): void {
  clearAuth()
  if (!expiredNotice) {
    expiredNotice = true
    ElMessage.error(message)
    window.setTimeout(() => { expiredNotice = false }, 1500)
  }
  const current = router.currentRoute.value
  if (current.path !== '/login') void router.push({ path: '/login', query: { redirect: current.fullPath } })
}

async function recoverUnauthorized(config: AuthConfig) {
  const current=getAuthSession(),currentToken=current?.accessToken
  const sameUser = config._authUserId === current?.userInfo.id&&config._authEpoch===current?.epoch
  // Do not replay an old user's write or clear a newer user's session.
  if (!sameUser || !currentToken) return Promise.reject(new Error('会话已变更，请重新操作'))
  if (config._retried || publicAuth(config.url)) {
    if(config._authToken===currentToken)handleUnauthorized()
    return Promise.reject(new Error('登录已过期'))
  }
  config._retried = true
  // Same-origin tabs serialize rotations; late 401s reuse the already rotated session.
  if (config._authToken === currentToken) {
    if(!navigator.locks){handleUnauthorized('当前浏览器无法安全刷新登录，请重新登录');throw new Error('无法协调登录刷新')}
    const controller=new AbortController(),timer=window.setTimeout(()=>controller.abort(),20000)
    try {
      await navigator.locks.request('trip-auth-refresh:'+baseURL,{signal:controller.signal},async()=>{
        window.clearTimeout(timer)
        const session=getAuthSession()
        if(!session||session.epoch!==config._authEpoch||session.userInfo.id!==config._authUserId)throw new Error('会话已变更，请重新操作')
        if(session.accessToken!==config._authToken)return
        try{
          const response=await refreshClient.post<ApiResponse<LoginResult>>('/auth/refresh',{refreshToken:session.refreshToken})
          if(response.data.code!==200)throw Object.assign(new Error(response.data.message||'登录刷新失败'),{code:response.data.code})
          if(getAuthSession()?.refreshToken!==session.refreshToken)throw new Error('会话已变更，请重新操作')
          setSession(response.data.data,session.epoch)
        }catch(error){
          const latest=getAuthSession(),status=axios.isAxiosError(error)?error.response?.status:undefined
          const code=axios.isAxiosError(error)?error.response?.data?.code:(error as {code?:number}).code
          if(latest?.epoch===session.epoch&&latest.refreshToken===session.refreshToken){
            if(status===401||code===401)handleUnauthorized()
            else ElMessage.error('登录验证暂时不可用，请稍后重试')
          }
          throw error
        }
      })
    }catch(error){
      if(controller.signal.aborted)ElMessage.error('登录刷新正在进行，请稍后重试')
      throw error
    }finally{window.clearTimeout(timer)}
  }
  const latest=getAuthSession()
  if(!latest||latest.userInfo.id!==config._authUserId||latest.epoch!==config._authEpoch||!latest.accessToken)throw new Error('会话已变更，请重新操作')
  return request(config)
}

request.interceptors.response.use(
  async response => {
    const config=response.config as AuthConfig,session=getAuthSession()
    if(config._authToken&&!publicAuth(config.url)&&(!session||session.epoch!==config._authEpoch||session.userInfo.id!==config._authUserId))throw new Error('会话已变更，请重新操作')
    const body = response.data as ApiResponse
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
      if (body.code === 401 && !publicAuth(response.config.url)) return recoverUnauthorized(response.config as AuthConfig)
      ElMessage.error(body.message || '请求失败')
      throw Object.assign(new Error(body.message || '请求失败'), { code: body.code })
    }
    return response
  },
  async error => {
    if (axios.isCancel(error)) return Promise.reject(error)
    const status = error.response?.status
    const body = error.response?.data as ApiResponse | undefined
    if ((status === 401 || body?.code === 401) && error.config && !publicAuth(error.config.url)) return recoverUnauthorized(error.config)
    ElMessage.error(status === 403 ? '无权限操作' : body?.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  },
)

export async function requestData<T>(config: AxiosRequestConfig, opts?: { wrapper?: boolean }): Promise<T> {
  const response = await request(config)
  return opts?.wrapper ? response.data as T : (response.data as ApiResponse<T>).data
}
export default request
