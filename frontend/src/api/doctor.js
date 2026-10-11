import request from './request'

/** 获取工作台仪表盘数据 */
export function getWorkspaceData() {
  return request.get('/doctor/workspace')
}

/** 获取膳食计划列表 */
export function getDietPlans(params) {
  return request.get('/doctor/diet-plans', { params })
}

/** 获取膳食计划详情 */
export function getDietPlanDetail(id) {
  return request.get(`/doctor/diet-plan/${id}`)
}

/** 创建膳食计划 */
export function createDietPlan(data) {
  return request.post('/doctor/diet-plan', data)
}

/** 更新膳食计划 */
export function updateDietPlan(id, data) {
  return request.put(`/doctor/diet-plan/${id}`, data)
}

/** 删除膳食计划 */
export function deleteDietPlan(id) {
  return request.delete(`/doctor/diet-plan/${id}`)
}

/** 获取患者膳食计划列表 */
export function getPatientDietPlans(patientId) {
  return request.get(`/doctor/patient/${patientId}/diet-plans`)
}

/** 发起咨询 */
export function createConsultation(data) {
  return request.post('/doctor/consultation', data)
}

/** 获取医师咨询列表 */
export function getDoctorConsultations(params) {
  return request.get('/doctor/consultations', { params })
}

/** 获取我的咨询列表 */
export function getMyConsultations(params) {
  return request.get('/doctor/my-consultations', { params })
}

/** 获取咨询详情 */
export function getConsultationDetail(id) {
  return request.get(`/doctor/consultation/${id}`)
}

/** 回复咨询 */
export function replyConsultation(id, data) {
  return request.post(`/doctor/consultation/${id}/reply`, data)
}

/** 关闭咨询 */
export function closeConsultation(id) {
  return request.put(`/doctor/consultation/${id}/close`)
}

/** 获取患者列表 */
export function getPatients(params) {
  return request.get('/doctor/patients', { params })
}