import axios from 'axios'
import { message } from 'ant-design-vue'

// 创建 Axios 实例，配置基础路径、超时时间和跨域凭证
const myAxios = axios.create({
  baseURL: 'http://localhost:8222', // 后端接口基础路径
  timeout: 60000, // 请求超时时间（60秒）
  withCredentials: true, // 允许跨域请求携带 cookies
})

/**
 * 全局请求拦截器
 * 作用：在请求发送前对配置进行处理（如添加请求头、token等）
 */
myAxios.interceptors.request.use(
  function (config) {
    // 可在此处添加统一请求头，例如：
    // config.headers.Authorization = `Bearer ${getToken()}`
    return config
  },
  function (error) {
    // 请求发送失败时的处理（如网络错误）
    return Promise.reject(error)
  },
)

/**
 * 全局响应拦截器
 * 作用：对后端响应进行统一处理（如错误码判断、登录状态校验）
 */
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response // 解构后端返回的响应数据

    // 处理未登录状态（错误码 40100）
    if (data.code === 40100) {
      // 过滤特殊场景：
      // 1. 排除获取登录用户信息的请求（避免循环跳转）
      // 2. 排除当前已在登录页面的情况
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        // 跳转到登录页，并携带当前页面地址作为重定向参数
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }

    return response // 将处理后的响应返回给调用方
  },
  function (error) {
    // 处理响应错误（如 404、500 等状态码）
    // 可在此处添加统一错误提示，例如：
    // message.error('请求失败，请稍后重试')
    return Promise.reject(error)
  },
)

export default myAxios
