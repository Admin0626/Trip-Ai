import request from '@/api/request'

export interface UserProfile extends UserInfo { phone?: string; email?: string; createTime?: string }
export interface ProfileInput { nickname: string; avatar: string; phone: string; email: string; city: string }
export interface UserStats { favoriteCount: number; bookingCount: number; commentCount: number; planCount: number; chatCount: number }
export interface UserPreference {
  preferenceTags: string[]
  avoidTags: string[]
  budgetMin: number
  budgetMax: number
  preferredDays: number | null
  companions: 'single' | 'couple' | 'family' | 'group' | ''
  pace: 'relaxed' | 'normal' | 'intense' | ''
}
export const profileApi = () => request.get<ApiResponse<UserProfile>>('/user/profile').then(r => r.data.data)
export const updateProfileApi = (input: ProfileInput) => request.put<ApiResponse<UserProfile>>('/user/profile', input).then(r => r.data.data)
export const statsApi = () => request.get<ApiResponse<UserStats>>('/user/stats').then(r => r.data.data)
export const preferenceApi = () => request.get<ApiResponse<UserPreference>>('/user/preference').then(r => r.data.data)
export const savePreferenceApi = (input: UserPreference) => request.put<ApiResponse<UserPreference>>('/user/preference', input).then(r => r.data.data)
export const changePasswordApi = (input: { oldPassword: string; newPassword: string }) => request.put<ApiResponse<void>>('/user/password', input).then(r => r.data.data)
export async function uploadImageApi(file: File): Promise<{ url: string; name: string }> {
  const data = new FormData()
  data.append('file', file)
  return request.post<ApiResponse<{ url: string; name: string }>>('/file/upload', data).then(r => r.data.data)
}
