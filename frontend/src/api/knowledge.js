import request from './request'

/**
 * 获取热门知识列表（首页使用，无需登录）
 * @param {number} size 返回条数，默认 6
 */
export function getHotKnowledge(size = 6) {
  return request({
    url: '/knowledge/hot',
    method: 'get',
    params: { size }
  })
}

/**
 * 获取知识列表
 */
export function getKnowledgeList(params) {
  return request({
    url: '/knowledge/list',
    method: 'get',
    params
  })
}

/**
 * 搜索知识
 */
export function searchKnowledge(keyword, page = 1, size = 10) {
  return request({
    url: '/knowledge/search',
    method: 'get',
    params: { keyword, page, size }
  })
}

/**
 * 获取知识详情
 */
export function getKnowledgeDetail(id) {
  return request({
    url: `/knowledge/${id}`,
    method: 'get'
  })
}

/**
 * 发布知识
 */
export function publishKnowledge(data) {
  return request({
    url: '/knowledge/publish',
    method: 'post',
    data
  })
}

/**
 * 更新知识
 */
export function updateKnowledge(id, data) {
  return request({
    url: `/knowledge/${id}`,
    method: 'put',
    data
  })
}

/**
 * 点赞知识
 */
export function likeKnowledge(id) {
  return request({
    url: `/knowledge/${id}/like`,
    method: 'post'
  })
}

/**
 * 取消点赞
 */
export function unlikeKnowledge(id) {
  return request({
    url: `/knowledge/${id}/like`,
    method: 'delete'
  })
}

/**
 * 收藏知识
 */
export function collectKnowledge(id) {
  return request({
    url: `/knowledge/${id}/collect`,
    method: 'post'
  })
}

/**
 * 取消收藏
 */
export function uncollectKnowledge(id) {
  return request({
    url: `/knowledge/${id}/collect`,
    method: 'delete'
  })
}

/**
 * 获取评论列表
 */
export function getKnowledgeComments(id, page = 1, size = 10) {
  return request({
    url: `/knowledge/${id}/comments`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 评论知识
 */
export function commentKnowledge(data) {
  return request({
    url: '/knowledge/comment',
    method: 'post',
    data
  })
}

/**
 * 删除知识
 */
export function deleteKnowledge(id) {
  return request({
    url: `/knowledge/${id}`,
    method: 'delete'
  })
}

/**
 * 获取用户发布的知识
 */
export function getUserPublishedKnowledge(userId, params) {
  return request({
    url: `/knowledge/user/${userId}/published`,
    method: 'get',
    params
  })
}

/**
 * 获取用户点赞的知识
 */
export function getUserLikedKnowledge(userId, params) {
  return request({
    url: `/knowledge/user/${userId}/liked`,
    method: 'get',
    params
  })
}

/**
 * 获取用户收藏的知识
 */
export function getCollectedKnowledge(userId, params) {
  return request({
    url: `/knowledge/user/${userId}/collected`,
    method: 'get',
    params
  })
}
