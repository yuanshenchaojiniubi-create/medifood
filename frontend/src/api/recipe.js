import request from './request'

/**
 * 获取热门食谱列表（首页使用，无需登录）
 * @param {number} size 返回条数，默认 6
 */
export function getHotRecipes(size = 6) {
  return request({
    url: '/recipe/hot',
    method: 'get',
    params: { size }
  })
}

/**
 * 获取食谱列表
 */
export function getRecipeList(params) {
  return request({
    url: '/recipe/list',
    method: 'get',
    params
  })
}

/**
 * 获取节气/天气饮食推荐（首页使用，无需登录）
 * @param {Object} options - { lat, lng, useDefault }
 */
export function getSeasonalDiet(options = {}) {
  return request({
    url: '/recipe/seasonal-diet',
    method: 'get',
    params: options
  })
}

/**
 * 搜索食谱
 */
export function searchRecipe(keyword, page = 1, size = 10) {
  return request({
    url: '/recipe/search',
    method: 'get',
    params: { keyword, page, size }
  })
}

/**
 * 获取食谱详情
 */
export function getRecipeDetail(id) {
  return request({
    url: `/recipe/${id}`,
    method: 'get'
  })
}

/**
 * 发布食谱
 */
export function publishRecipe(data) {
  return request({
    url: '/recipe/publish',
    method: 'post',
    data
  })
}

/**
 * 更新食谱
 */
export function updateRecipe(id, data) {
  return request({
    url: `/recipe/${id}`,
    method: 'put',
    data
  })
}

/**
 * 点赞食谱
 */
export function likeRecipe(id) {
  return request({
    url: `/recipe/${id}/like`,
    method: 'post'
  })
}

/**
 * 取消点赞
 */
export function unlikeRecipe(id) {
  return request({
    url: `/recipe/${id}/like`,
    method: 'delete'
  })
}

/**
 * 评论食谱
 */
export function commentRecipe(id, content, parentId) {
  return request({
    url: `/recipe/${id}/comment`,
    method: 'post',
    params: { content, parentId }
  })
}

/**
 * 收藏食谱
 */
export function collectRecipe(id) {
  return request({
    url: `/recipe/${id}/collect`,
    method: 'post'
  })
}

/**
 * 取消收藏
 */
export function uncollectRecipe(id) {
  return request({
    url: `/recipe/${id}/collect`,
    method: 'delete'
  })
}

/**
 * 删除食谱
 */
export function deleteRecipe(id) {
  return request({
    url: `/recipe/${id}`,
    method: 'delete'
  })
}

/**
 * 获取用户发布的食谱
 */
export function getUserPublishedRecipes(userId, params) {
  return request({
    url: `/recipe/user/${userId}/published`,
    method: 'get',
    params
  })
}

/**
 * 获取用户点赞的食谱
 */
export function getUserLikedRecipes(userId, params) {
  return request({
    url: `/recipe/user/${userId}/liked`,
    method: 'get',
    params
  })
}

/**
 * 获取用户收藏的食谱
 */
export function getUserCollectedRecipes(userId, params) {
  return request({
    url: `/recipe/user/${userId}/collected`,
    method: 'get',
    params
  })
}

/**
 * 获取所有食谱标签
 */
export function getRecipeTags() {
  return request({
    url: '/recipe/tags',
    method: 'get'
  })
}

/**
 * 创建食谱标签
 */
export function createRecipeTag(tagName, tagType = 'custom') {
  return request({
    url: '/recipe/tags',
    method: 'post',
    data: { tagName, tagType }
  })
}
