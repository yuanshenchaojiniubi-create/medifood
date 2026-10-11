import request from './request'

/**
 * 内容审核台账列表
 * @param {Object} params { contentType, status, page, size }
 */
export function listAuditRecords(params) {
  return request.get('/community/admin/audit/list', { params })
}

/** 待审数量统计 */
export function auditStats() {
  return request.get('/community/admin/audit/stats')
}

/** 审核通过 */
export function approveAudit(recordId, remark) {
  return request.post(`/community/admin/audit/${recordId}/approve`, { remark })
}

/** 审核拒绝（删除内容），warn=true 时同时警告用户 */
export function rejectAudit(recordId, warn, remark) {
  return request.post(`/community/admin/audit/${recordId}/reject?warn=${warn}`, { remark })
}
