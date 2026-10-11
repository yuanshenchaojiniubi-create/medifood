import request from './request'

export function getConversations() {
  return request({
    url: '/message/conversations',
    method: 'get'
  })
}

export function getMessagesWithUser(peerUserId, page = 1, size = 50) {
  return request({
    url: `/message/conversation/${peerUserId}`,
    method: 'get',
    params: { page, size }
  })
}

export function sendMessage(data) {
  return request({
    url: '/message/send',
    method: 'post',
    data
  })
}

export function getChatUnreadCount() {
  return request({
    url: '/message/unread-count',
    method: 'get'
  })
}

export function markRead(peerUserId) {
  return request({
    url: `/message/read/${peerUserId}`,
    method: 'post'
  })
}

export function getChatStatus(peerUserId) {
  return request({
    url: `/message/chat-status/${peerUserId}`,
    method: 'get'
  })
}

export function shareMessage(data) {
  return request({
    url: '/message/share',
    method: 'post',
    data
  })
}

export function uploadVoice(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/message/upload-voice',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function recallMessage(messageId) {
  return request({
    url: `/message/recall/${messageId}`,
    method: 'post'
  })
}

export function getUnifiedUnreadCount() {
  return request({
    url: '/message/unified-unread',
    method: 'get'
  })
}

export function getOnlineStatus(userId) {
  return request({
    url: `/message/online-status/${userId}`,
    method: 'get'
  })
}

export function getOnlineStatusBatch(userIds) {
  return request({
    url: '/message/online-status/batch',
    method: 'post',
    data: userIds
  })
}

// ==================== 好友申请 ====================

export function sendFriendRequest(toUserId, message) {
  return request({
    url: '/message/friend-request/send',
    method: 'post',
    data: { toUserId, message }
  })
}

export function acceptFriendRequest(requestId) {
  return request({
    url: `/message/friend-request/accept/${requestId}`,
    method: 'post'
  })
}

export function rejectFriendRequest(requestId) {
  return request({
    url: `/message/friend-request/reject/${requestId}`,
    method: 'post'
  })
}

export function getPendingRequests() {
  return request({
    url: '/message/friend-request/pending',
    method: 'get'
  })
}

export function getSentRequests() {
  return request({
    url: '/message/friend-request/sent',
    method: 'get'
  })
}

export function getRequestStatus(peerUserId) {
  return request({
    url: `/message/friend-request/status/${peerUserId}`,
    method: 'get'
  })
}