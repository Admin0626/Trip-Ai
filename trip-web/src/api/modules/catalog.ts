import request from '@/api/request'

export interface DestinationInput {
  name:string; province:string; city:string; longitude:number; latitude:number; coverImg:string;
  intro:string; tags:string[]; bestSeason:string; avgCost:number; status:number
}
export interface AttractionInput {
  name:string; coverImg:string; intro:string; address:string; longitude:number; latitude:number;
  ticketPrice:number; openTime:string; durationMin:number; tags:string[]; status:number
}
export interface RouteInput {
  title:string; subtitle:string; coverImg:string; destinationId:number; days:number; price:number;
  difficulty:number; tags:string[]; highlights:string; notice:string; recommendWeight:number;
  isTop:number; quotaPerDay:number; status:number; dayList:CatalogDay[]
}
export interface CatalogItem {
  title:string; timePoint:string; attractionId:number; activity:string; transport:string;
  hotel:string; meal:string; durationMin:number; cost:number; tips:string
}
export interface CatalogDay { title:string; summary:string; items:CatalogItem[] }
export interface CatalogRoute extends RouteInput { id:number; destinationName:string }
export interface BannerInput {
  title:string; imageUrl:string; linkType:'NONE'|'ROUTE'|'DESTINATION'|'URL'; linkValue:string;
  sortNo:number; status:number; startTime:string|null; endTime:string|null
}
export interface Banner extends BannerInput { id:number }
export type CatalogFilter = {current:number; size?:number; keyword?:string; status?:number; destinationId?:number}
const page = <T>(path:string, params:object) => request.get<ApiResponse<PageResult<T>>>(path,{params}).then(r=>r.data.data)
const get = <T>(path:string) => request.get<ApiResponse<T>>(path).then(r=>r.data.data)
const write = (path:string, data:object, id?:number) => id === undefined
  ? request.post<ApiResponse<number>>(path,data).then(r=>r.data.data)
  : request.put<ApiResponse<void>>(`${path}/${id}`,data).then(r=>r.data.data)
export const catalogDestinations = (params:CatalogFilter) => page<DestinationVO>('/admin/catalog/destinations',{size:20,...params})
export const catalogDestination = (id:number) => get<DestinationVO>(`/admin/catalog/destinations/${id}`)
export const saveDestination = (data:DestinationInput,id?:number) => write('/admin/catalog/destinations',data,id)
export const deleteDestination = (id:number) => request.delete(`/admin/catalog/destinations/${id}`)
export const destinationStatus = (id:number,status:number) => request.put(`/admin/catalog/destinations/${id}/status`,{status})
export const catalogAttractions = (id:number) => get<AttractionVO[]>(`/admin/catalog/destinations/${id}/attractions`)
export const saveAttraction = (parent:number,data:AttractionInput,id?:number) => id === undefined
  ? request.post<ApiResponse<number>>(`/admin/catalog/destinations/${parent}/attractions`,data).then(r=>r.data.data)
  : request.put(`/admin/catalog/attractions/${id}`,data)
export const deleteAttraction = (id:number) => request.delete(`/admin/catalog/attractions/${id}`)
export const catalogRoutes = (params:CatalogFilter) => page<CatalogRoute>('/admin/catalog/routes',{size:20,...params})
export const catalogRoute = (id:number) => get<CatalogRoute>(`/admin/catalog/routes/${id}`)
export const saveRoute = (data:RouteInput,id?:number) => write('/admin/catalog/routes',data,id)
export const deleteRoute = (id:number) => request.delete(`/admin/catalog/routes/${id}`)
export const routeStatus = (id:number,status:number) => request.put(`/admin/catalog/routes/${id}/status`,{status})
export const routeTop = (id:number,isTop:number) => request.put(`/admin/catalog/routes/${id}/top`,{isTop})
export const bannerPage = (current:number) => page<Banner>('/admin/banner/page',{current,size:20})
export const saveBanner = (data:BannerInput,id?:number) => write('/admin/banner',data,id)
export const deleteBanner = (id:number) => request.delete(`/admin/banner/${id}`)
export const activeBanners = () => get<Banner[]>('/banner/list')
