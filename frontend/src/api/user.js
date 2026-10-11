import request from './request'

/**
 * 用户登录
 */
export function login(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

/**
 * 用户注册
 */
export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

/**
 * 获取用户信息（当前登录用户）
 */
export function getUserInfo() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

/**
 * 获取指定用户信息
 */
export function getUserInfoById(userId, currentUserId) {
  return request({
    url: `/user/${userId}/info`,
    method: 'get',
    params: currentUserId ? { currentUserId } : {}
  })
}

/**
 * 更新用户信息
 */
export function updateUserInfo(data) {
  return request({
    url: '/user/info',
    method: 'put',
    data
  })
}

/**
 * 上传头像
 */
export function uploadAvatar(avatarUrl) {
  return request({
    url: '/user/avatar',
    method: 'post',
    params: { avatarUrl }
  })
}

/**
 * 获取医师认证状态
 */
export function getDoctorCertificationStatus() {
  return request({
    url: '/user/certification/doctor/status',
    method: 'get'
  })
}

/**
 * 提交医师认证申请
 */
export function applyDoctorCertification(data) {
  return request({
    url: '/user/certification/doctor',
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data
  })
}

/**
 * 修改密码
 */
export function changePassword(data) {
  return request({
    url: '/user/password',
    method: 'post',
    data
  })
}

