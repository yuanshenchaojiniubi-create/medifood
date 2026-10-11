import request from './request'

export function getBanners() {
  return request.get('/user/banners')
}

export function listBanners() {
  return request.get('/admin/banners')
}

export function createBanner(data) {
  return request.post('/admin/banner', data)
}

export function updateBanner(id, data) {
  return request.put(`/admin/banner/${id}`, data)
}

export function deleteBanner(id) {
  return request.delete(`/admin/banner/${id}`)
}