<template>
  <div id="userLoginPage">
    <!-- 登录页标题 -->
    <h2 class="title">Tonight云图库 - 用户登录</h2>
    <!-- 登录页描述 -->
    <div class="desc">Tonight图库</div>
    <!-- 登录表单：绑定表单数据，配置校验规则，提交触发handleSubmit -->
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <!-- 账号输入框：必填校验 -->
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <!-- 密码输入框：必填 + 最小长度8位校验 -->
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <!-- 注册引导：无账号时跳转注册页 -->
      <div class="tips">
        没有账号？
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <!-- 登录按钮：占满宽度 -->
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLoginUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { RouterLink } from 'vue-router';

// 表单数据模型：绑定登录请求所需的账号和密码，遵循API.UserLoginRequest类型定义
const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

// 路由实例：用于登录成功后跳转页面
const router = useRouter()
// 登录用户状态Store：用于同步登录后的用户信息
const loginUserStore = useLoginUserStore()

/**
 * 表单提交处理函数
 * @param values 表单校验通过后的输入值
 */
const handleSubmit = async (values: any) => {
  // 调用登录接口，传入表单数据
  const res = await userLoginUsingPost(values)
  // 登录成功（后端返回code=0且有数据）
  if (res.data.code === 0 && res.data.data) {
    // 重新拉取最新登录用户信息并更新到全局状态
    await loginUserStore.fetchLoginUser()
    // 显示登录成功提示
    message.success('登录成功')
    // 跳转到主页，replace: true 避免回退到登录页
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    // 登录失败，显示错误信息（后端返回的提示文案）
    message.error('登录失败，' + res.data.message)
  }
}

</script>
<style scoped>
/* 登录页容器样式：限制最大宽度，水平居中 */
#userLoginPage {
  max-width: 360px;
  margin: 0 auto;
}

/* 标题样式：居中对齐，底部间距 */
.title {
  text-align: center;
  margin-bottom: 16px;
}

/* 描述文本样式：居中对齐，灰色，底部间距 */
.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

/* 注册引导文本样式：右对齐，灰色，小号字体，底部间距 */
.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}
</style>
