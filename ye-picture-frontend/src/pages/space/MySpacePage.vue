<template>
  <div id="mySpace">
    <!-- 优化加载状态，替换生硬文字提示 -->
    <a-spin spinning="true" tip="正在加载我的空间..." v-if="loading">
      <div style="height: 100px"></div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { listSpaceVoByPageUsingPost } from '@/api/spaceController'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loading = ref(true) // 加载状态，提升用户体验

// 检查用户的私有空间（spaceType=0）
const checkUserPrivateSpace = async () => {
  const loginUser = loginUserStore.loginUser
  // 1. 未登录 → 跳转到登录页
  if (!loginUser?.id) {
    router.replace('/user/login')
    message.info('请先登录后访问我的空间')
    loading.value = false
    return
  }

  try {
    // 2. 调用现有接口，查询当前用户的所有空间
    const res = await listSpaceVoByPageUsingPost({
      userId: loginUser.id,
      current: 1,
      pageSize: 1,
      spaceType: 0,
    })


    if (res.data.code === 0) {
      const allSpaces = res.data.data?.records ?? []
      // 3. 筛选出私有空间（spaceType=0）
      const privateSpace = allSpaces.find(space => space.spaceType === SPACE_TYPE_ENUM.PRIVATE)

      // 4. 存在私有空间 → 跳转到该空间详情页
      if (privateSpace) {
        router.replace(`/space/${privateSpace.id}`)
      }
      // 5. 不存在私有空间 → 跳转到创建空间页（预设类型为“私有空间”）
      else {
        router.replace(`/add_space?type=${SPACE_TYPE_ENUM.PRIVATE}`)
        message.warn('未检测到你的私有空间，请先创建')
      }
    } else {
      message.error('加载空间失败：' + res.data.message)
    }
  } catch (error: any) {
    message.error('网络异常：' + (error.message || '加载我的空间失败'))
  } finally {
    loading.value = false // 无论成功失败，都关闭加载状态
  }
}

// 页面加载时执行检查
onMounted(() => {
  checkUserPrivateSpace()
})
</script>

<style scoped>
#mySpace {
  padding: 20px;
  text-align: center;
  background: #fff;
  min-height: calc(100vh - 120px);
}
</style>
