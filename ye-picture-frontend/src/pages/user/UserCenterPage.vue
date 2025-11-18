<template>
  <div id="userCenterPage">
    <a-page-header title="个人资料" :sub-title="isEditing ? '编辑你的个人信息' : '查看你的个人信息'" />

    <a-card :title="isEditing ? '编辑资料' : '我的资料'" :loading="loading">
      <template #extra>
        <a-space>
          <a-button v-if="!isEditing" type="primary" @click="enterEdit">编辑资料</a-button>
          <template v-else>
            <a-button @click="onCancel">取消</a-button>
            <a-button type="primary" :loading="submitting" @click="onSubmit">保存</a-button>
          </template>
        </a-space>
      </template>

      <div v-if="!isEditing">
        <div class="avatar-box">
          <a-avatar :size="96" :src="loginUser?.userAvatar" shape="circle">
            {{ loginUser?.userName?.[0] || 'U' }}
          </a-avatar>
        </div>
        <a-descriptions bordered :column="1" size="middle">
          <a-descriptions-item label="用户昵称">
            {{ loginUser?.userName || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="账号">
            {{ loginUser?.userAccount || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="个性签名">
            {{ loginUser?.userProfile || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="角色">
            <a-tag color="blue">{{ loginUser?.userRole || 'user' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatDateTime(loginUser?.createTime) || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="最近更新">
            {{ formatDateTime(loginUser?.updateTime) || '-' }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <div v-else>
        <a-form :model="formState" :label-col="{ span: 5 }" :wrapper-col="{ span: 19 }" @finish.prevent>
          <a-form-item label="头像">
            <div class="avatar-editor">
              <a-avatar :size="72" :src="formState.userAvatar" class="mr8" />
              <PictureUpload :picture="avatarAsPicture" :on-success="onAvatarUploaded" />
            </div>
            <div v-if="formState.userAvatar" class="avatar-url">{{ formState.userAvatar }}</div>
          </a-form-item>

          <a-form-item label="用户昵称" name="userName" :rules="[{ required: true, message: '请输入昵称' }]">
            <a-input v-model:value="formState.userName" placeholder="请输入昵称" allow-clear />
          </a-form-item>

          <a-form-item label="账号" name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.userAccount" placeholder="请输入账号" allow-clear />
          </a-form-item>

          <a-form-item label="密码" name="userPassword" :rules="[{ min: 8, message: '密码不能小于 8 位' }]">
            <a-input-password v-model:value="formState.userPassword" placeholder="请输入新密码（不修改可留空）" allow-clear />
          </a-form-item>

          <a-form-item label="个性签名" name="userProfile">
            <a-textarea v-model:value="formState.userProfile" :rows="4" placeholder="介绍一下自己吧" allow-clear />
      </a-form-item>
    </a-form>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import PictureUpload from '@/components/picture/PictureUpload.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userEditUsingPost } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()
const loading = ref<boolean>(false)
const submitting = ref<boolean>(false)
const isEditing = ref<boolean>(false)

onMounted(async () => {
  loading.value = true
  try {
    await loginUserStore.fetchLoginUser()
  } finally {
    loading.value = false
  }
})

const loginUser = computed(() => loginUserStore.loginUser)

const formState = reactive<{
  id?: number
  userAvatar?: string
  userName?: string
  userProfile?: string
  userAccount?: string
  userPassword?: string
}>({})

watch(
  loginUser,
  (val) => {
    if (!val) return
    if (!isEditing.value) {
      formState.id = val.id
      formState.userAvatar = val.userAvatar
      formState.userName = val.userName
      formState.userProfile = val.userProfile
      formState.userAccount = val.userAccount
      formState.userPassword = undefined
    }
  },
  { immediate: true }
)

const avatarAsPicture = computed<API.PictureVO | undefined>(() => {
  if (!formState.userAvatar) return undefined
  return { url: formState.userAvatar }
})

const onAvatarUploaded = (pic: API.PictureVO) => {
  formState.userAvatar = pic.url || pic.thumbnailUrl
  message.success('头像已更新，记得保存')
}

const enterEdit = () => {
  if (!loginUser.value) return
  formState.id = loginUser.value.id
  formState.userAvatar = loginUser.value.userAvatar
  formState.userName = loginUser.value.userName
  formState.userProfile = loginUser.value.userProfile
  formState.userAccount = loginUser.value.userAccount
  formState.userPassword = undefined
  isEditing.value = true
}

const onCancel = () => {
  isEditing.value = false
}

function formatDateTime(input?: string) {
  if (!input) return ''
  const d = new Date(input)
  if (isNaN(d.getTime())) return input
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  const y = d.getFullYear()
  const m = pad(d.getMonth() + 1)
  const day = pad(d.getDate())
  const hh = pad(d.getHours())
  const mm = pad(d.getMinutes())
  return `${y}-${m}-${day} ${hh}:${mm}`
}

const onSubmit = async () => {
  submitting.value = true
  try {
    const payload: API.UserEditRequest = {
      userAvatar: formState.userAvatar,
      userName: formState.userName,
      userProfile: formState.userProfile,
      userAccount: formState.userAccount,
      userPassword: formState.userPassword || undefined,
    }
    const res = await userEditUsingPost(payload)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      // 出于安全考虑，清空本地输入的密码
      formState.userPassword = undefined
      message.success('资料保存成功')
      isEditing.value = false
  } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (e) {
    message.error('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
#userCenterPage {
  max-width: 720px;
  margin: 0 auto;
}

.avatar-box {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mr8 {
  margin-right: 8px;
}

.avatar-url {
  color: #999;
  font-size: 12px;
  margin-top: 8px;
  word-break: break-all;
}
</style>
