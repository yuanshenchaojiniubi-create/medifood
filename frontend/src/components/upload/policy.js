import axios from 'axios'
import request from '@/api/request.js'
import { useUserStore } from '@/store/user'

// 获取 OSS 上传签名：直接使用 axios 请求后端原始接口，绕过统一响应拦截器
export async function policy() {
    try {
        const base = request?.defaults?.baseURL || ''
        const url = base.replace(/\/$/, '') + '/third/get_post_signature_for_oss_upload'
        // 签名接口需要登录态（网关已启用 JWT 鉴权），手动携带 token；
        // 仍用原生 axios 以绕过统一响应拦截器（签名接口返回非 {code,data} 格式）
        const userStore = useUserStore()
        const headers = userStore?.token ? { Authorization: `Bearer ${userStore.token}` } : {}
        const resp = await axios.get(url, { headers })
        // 返回后端原始 body（有些签名接口不遵循 {code,data} 格式）
        return resp.data
    } catch (err) {
        // 将错误抛出给调用者处理
        throw err
    }
}