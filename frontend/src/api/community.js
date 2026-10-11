import request from './request'

/**
 * 发布动态
 */
export function publishPost(data) {
  return request({
    url: '/community/post',
    method: 'post',
    data
  })
}

/**
 * 获取动态流
 */
export function getFeed(page = 1, size = 10) {
  return request({
    url: '/community/feed',
    method: 'get',
    params: { page, size }
  })
}

/**
 * 删除动态
 */
export function deletePost(id) {
  return request({
    url: `/community/post/${id}`,
    method: 'delete'
  })
}

/**
 * 获取动态详情
 */
export function getPostDetail(id) {
  return request({
    url: `/community/post/${id}`,
    method: 'get'
  })
}

/**
 * 获取动态评论（顶层含回复）
 */
export function getPostComments(id, page = 1, size = 10) {
  return request({
    url: `/community/post/${id}/comments`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 搜索动态
 */
export function searchPosts(keyword, page = 1, size = 10) {
  return request({
    url: '/community/search',
    method: 'get',
    params: { keyword, page, size }
  })
}

/**
 * 获取热门动态
 */
export function getHotPosts(page = 1, size = 10) {
  return request({
    url: '/community/hot',
    method: 'get',
    params: { page, size }
  })
}

/**
 * 点赞动态
 */
export function likePost(id) {
  return request({
    url: `/community/${id}/like`,
    method: 'post'
  })
}

/**
 * 取消点赞
 */
export function unlikePost(id) {
  return request({
    url: `/community/${id}/like`,
    method: 'delete'
  })
}

/**
 * 评论动态
 */
export function commentPost(id, content, parentId) {
  return request({
    url: `/community/${id}/comment`,
    method: 'post',
    params: { content, parentId }
  })
}

/**
 * 关注用户
 */
export function followUser(userId) {
  return request({
    url: `/community/user/${userId}/follow`,
    method: 'post'
  })
}

/**
 * 取消关注
 */
export function unfollowUser(userId) {
  return request({
    url: `/community/user/${userId}/follow`,
    method: 'delete'
  })
}

/**
 * 获取粉丝列表
 */
export function getFollowers(userId, page = 1, size = 10) {
  return request({
    url: `/community/user/${userId}/followers`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取关注列表
 */
export function getFollowing(userId, page = 1, size = 10) {
  return request({
    url: `/community/user/${userId}/following`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取互关列表
 */
export function getMutualFollows(userId, page = 1, size = 10) {
  return request({
    url: `/community/user/${userId}/mutual`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取用户发布的动态
 */
export function getUserPosts(userId, page = 1, size = 10) {
  return request({
    url: `/community/user/${userId}/posts`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取用户点赞的动态
 */
export function getUserLikedPosts(userId, page = 1, size = 10) {
  return request({
    url: `/community/user/${userId}/liked`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取用户收藏的动态
 */
export function getUserCollectedPosts(userId, page = 1, size = 10) {
  return request({
    url: `/community/user/${userId}/collected`,
    method: 'get',
    params: { page, size }
  })
}
