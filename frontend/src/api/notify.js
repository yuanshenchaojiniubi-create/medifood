import request from './request'

export function listNotifications(page = 1, size = 10) {
  return request({
    url: '/community/notify/list',
    method: 'get',
    params: { page, size }
  })
}

export function markRead(id) {
  return request({
    url: `/community/notify/read/${id}`,
    method: 'post'
  })
}

export function markAggregateRead(key) {
  return request({
    url: '/community/notify/read-aggregate',
    method: 'post',
    params: { key }
  })
}

export function markAllRead() {
  return request({
    url: '/community/notify/read-all',
    method: 'post'
  })
}

export function getUnreadCount() {
  return request({
    url: '/community/notify/unread-count',
    method: 'get'
  })
}

export function deleteNotification(id) {
  return request({
    url: `/community/notify/${id}`,
    method: 'delete'
  })
}

export function deleteAggregateNotification(key) {
  return request({
    url: '/community/notify/aggregate',
    method: 'delete',
    params: { key }
  })
}

/**
 * 批量获取通知富文本数据（替代N+1查询）
 * 一次性传入所有 actorIds 和 entityInfos，后端批量查询返回
 */
export function enrichNotifications(actorIds, entityInfos) {
  return request({
    url: '/community/notify/enrich',
    method: 'post',
    data: { actorIds, entityInfos }
  })
}

/**
 * 获取聚合通知的所有参与者（谁赞了我）
 */
export function getAggregateActors(aggregateKey) {
  return request({
    url: '/community/notify/aggregate-actors',
    method: 'get',
    params: { key: aggregateKey }
  })
}
