<template>
  <a-modal
    class="image-out-painting"
    v-model:visible="visible"
    title="AI 扩图"
    :footer="false"
    @cancel="closeModal"
  >
    <a-row gutter="16">
      <a-col span="12">
        <h4>原始图片</h4>
        <img :src="picture?.url" :alt="picture?.name" style="max-width: 100%" />
      </a-col>
      <a-col span="12">
        <h4>扩图结果</h4>
        <img
          v-if="resultImageUrl"
          :src="resultImageUrl"
          :alt="picture?.name"
          style="max-width: 100%"
        />
      </a-col>
    </a-row>
    <div style="margin-bottom: 16px" />
    <a-flex gap="16" justify="center">
      <a-button type="primary" :loading="!!taskId" ghost @click="createTask">
        生成图片
      </a-button>
      <a-button type="primary" v-if="resultImageUrl" :loading="uploadLoading" @click="handleUpload">
        应用结果
      </a-button>
    </a-flex>
  </a-modal>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController'
import { message } from 'ant-design-vue'

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

// 结果图片 URL
const resultImageUrl = ref<string>()
// 任务 ID
const taskId = ref<string | null>(null)
// 轮询定时器
const pollingTimer = ref<number | null>(null)

/**
 * 创建扩图任务
 */
const createTask = async () => {
  if (!props.picture?.id) return

  try {
    const res = await createPictureOutPaintingTaskUsingPost({
      pictureId: props.picture.id,
      parameters: {
        xScale: 2,
        yScale: 2,
      },
    })

    if (res.data.code === 0 && res.data.data?.output?.taskId) {
      message.success('创建任务成功，请耐心等待，不要退出界面')
      taskId.value = res.data.data.output.taskId
      startPolling()
    } else {
      message.error('创建任务失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('创建任务失败:', error)
    message.error('创建任务失败，请稍后重试')
  }
}

/**
 * 开始轮询任务状态
 */
const startPolling = () => {
  if (!taskId.value) return

  pollingTimer.value = window.setInterval(async () => {
    try {
      const res = await getPictureOutPaintingTaskUsingGet({ taskId: taskId.value })
      if (res.data.code === 0 && res.data.data?.output) {
        const taskResult = res.data.data.output
        if (taskResult.taskStatus === 'SUCCEEDED') {
          message.success('扩图任务成功')
          resultImageUrl.value = taskResult.outputImageUrl
          clearPolling()
        } else if (taskResult.taskStatus === 'FAILED') {
          message.error('扩图任务失败')
          clearPolling()
        }
      }
    } catch (error) {
      console.error('轮询任务状态失败:', error)
      message.error('检测任务状态失败，请稍后重试')
      clearPolling()
    }
  }, 3000)
}

/**
 * 清理轮询定时器
 */
const clearPolling = () => {
  if (pollingTimer.value !== null) {
    window.clearInterval(pollingTimer.value)
    pollingTimer.value = null
    taskId.value = null
  }
}

/**
 * 组件卸载时清理定时器
 */
onUnmounted(() => {
  clearPolling()
})

/**
 * 弹窗可见性控制
 */
const visible = ref(false)

const openModal = () => {
  visible.value = true
}

const closeModal = () => {
  visible.value = false
}

defineExpose({
  openModal,
})

/**
 * 图片上传加载状态
 */
const uploadLoading = ref<boolean>(false)

/**
 * 处理上传结果图片
 */
const handleUpload = async () => {
  uploadLoading.value = true
  try {
    const params: API.UploadRequest = {
      fileUrl: resultImageUrl.value!,
      spaceId: props.spaceId,
    }
    if (props.picture) {
      params.id = props.picture.id
    }

    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    } else {
      message.error('图片上传失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('上传失败:', error)
    message.error('图片上传失败，请稍后重试')
  } finally {
    uploadLoading.value = false
  }
}
</script>

<style scoped>
.image-out-painting {
  text-align: center;
}
</style>
