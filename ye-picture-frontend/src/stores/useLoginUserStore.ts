import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUserUsingGet } from '@/api/userController.ts'

/**
 * 登录用户状态管理 Store
 * 负责存储和管理当前登录用户的信息
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  // 登录用户信息响应式变量，初始化为未登录状态
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录', // 默认显示"未登录"
  })

  /**
   * 从远程接口获取当前登录用户信息
   * 成功后更新 loginUser 状态
   */
  async function fetchLoginUser() {
    const res = await getLoginUserUsingGet()
    // 接口调用成功且返回有效数据时，更新用户信息
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    }
  }

  /**
   * 手动设置登录用户信息
   * @param newLoginUser - 新的用户信息对象
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  // 暴露状态和方法供组件使用
  return { loginUser, setLoginUser, fetchLoginUser }
})
