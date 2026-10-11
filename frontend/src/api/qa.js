import request from './request'

/**
 * 获取问题列表
 */
export function getQuestionList(params) {
  return request({
    url: '/qa/question/list',
    method: 'get',
    params
  })
}

/**
 * 搜索问题
 */
export function searchQuestion(keyword, page = 1, size = 10) {
  return request({
    url: '/qa/question/search',
    method: 'get',
    params: { keyword, page, size }
  })
}

/**
 * 获取问题详情
 */
export function getQuestionDetail(id) {
  return request({
    url: `/qa/question/${id}`,
    method: 'get'
  })
}

/**
 * 发布问题
 */
export function publishQuestion(data) {
  return request({
    url: '/qa/question',
    method: 'post',
    data
  })
}

/**
 * 点赞问题
 */
export function likeQuestion(id) {
  return request({
    url: `/qa/question/${id}/like`,
    method: 'post'
  })
}

/**
 * 取消点赞问题
 */
export function unlikeQuestion(id) {
  return request({
    url: `/qa/question/${id}/like`,
    method: 'delete'
  })
}

/**
 * 获取回答列表
 */
export function getAnswerList(questionId, page = 1, size = 10) {
  return request({
    url: `/qa/question/${questionId}/answers`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 发布回答
 */
export function publishAnswer(data) {
  return request({
    url: '/qa/answer',
    method: 'post',
    data
  })
}

/**
 * 点赞回答
 */
export function likeAnswer(id) {
  return request({
    url: `/qa/answer/${id}/like`,
    method: 'post'
  })
}

/**
 * 取消点赞回答
 */
export function unlikeAnswer(id) {
  return request({
    url: `/qa/answer/${id}/like`,
    method: 'delete'
  })
}

/**
 * 设置最佳答案
 */
export function setBestAnswer(answerId, questionId) {
  return request({
    url: `/qa/answer/${answerId}/best`,
    method: 'post',
    params: { questionId }
  })
}

/**
 * 获取用户发布的问题
 */
export function getUserQuestions(userId, page = 1, size = 10) {
  return request({
    url: `/qa/user/${userId}/questions`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取用户点赞的问题
 */
export function getUserLikedQuestions(userId, page = 1, size = 10) {
  return request({
    url: `/qa/user/${userId}/liked`,
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取用户收藏的问题
 */
export function getUserCollectedQuestions(userId, params) {
  return request({
    url: `/qa/question/user/${userId}/collected`,
    method: 'get',
    params
  })
}

/**
 * 删除问题
 */
export function deleteQuestion(id) {
  return request({
    url: `/qa/question/${id}`,
    method: 'delete'
  })
}
