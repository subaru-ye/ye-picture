<template>
  <div id="userRegisterPage">
    <!-- 注册页标题 -->
    <h2 class="title">Tonight云图库 - 用户注册</h2>
    <!-- 注册页描述 -->
    <div class="desc">智能协同云图库</div>
    <!-- 注册表单：绑定数据模型，左对齐标签，提交触发注册逻辑 -->
    <a-form
      :model="formState"
      name="basic"
      label-align="left"
      autocomplete="off"
      @finish="handleSubmit"
    >
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
      <!-- 确认密码输入框：必填 + 最小长度8位校验 -->
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请输入确认密码' },
          { min: 8, message: '确认密码不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请输入确认密码" />
      </a-form-item>
      <!-- 登录引导：已有账号时跳转登录页 -->
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <!-- 注册按钮：占满宽度 -->
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { userRegisterUsingPost } from '@/api/userController.ts'
import { useRouter } from 'vue-router'
import { reactive } from 'vue'
import { RouterLink } from 'vue-router';

// 表单数据模型：绑定注册所需字段，遵循API.UserRegisterRequest类型定义
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

// 路由实例：用于注册成功后跳转登录页
const router = useRouter()

/**
 * 表单提交处理函数：执行注册逻辑
 * @param values 表单校验通过后的输入值
 */
const handleSubmit = async (values: any) => {
  // 前端校验：两次输入密码一致性检查
  if (formState.userPassword !== formState.checkPassword) {
    message.error('二次输入的密码不一致')
    return // 校验失败，终止后续流程
  }

  // 调用注册接口，传入表单数据
  const res = await userRegisterUsingPost(values)

  // 注册成功（后端返回code=0且有数据）
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功')
    // 跳转到登录页，replace: true 避免回退到注册页
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    // 注册失败，显示后端返回的错误信息
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
/* 注册页容器样式：限制最大宽度，水平居中 */
#userRegisterPage {
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

/* 登录引导文本样式：右对齐，灰色，小号字体，底部间距 */
.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}
</style>
