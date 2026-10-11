import request from './request'

/** 待审核医师认证列表 */
export function listPendingDoctorCerts() {
  return request.get('/admin/doctor-certifications/pending')
}

/** 审核医师认证 */
export function reviewDoctorCert(data) {
  return request.post('/admin/doctor-certifications/review', data)
}
