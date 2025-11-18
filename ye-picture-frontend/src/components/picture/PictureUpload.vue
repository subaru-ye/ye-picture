<template>
  <div class="picture-upload">
    <a-upload
      list-type="picture-card"
      :show-upload-list="false"
      :custom-request="handleUpload"
      :before-upload="beforeUpload"
    >
      <img v-if="picture?.url" :src="picture?.url" alt="avatar" />
      <div v-else>
        <loading-outlined v-if="loading"></loading-outlined>
        <plus-outlined v-else></plus-outlined>
        <div class="ant-upload-text">点击或拖拽上传图片</div>
      </div>
    </a-upload>
  </div>
</template>
<script lang="ts" setup>
/**
 * 图片上传组件
 * 支持图片上传、预览和编辑功能
 * 使用 Ant Design Vue 的 Upload 组件实现
 */
import { ref } from 'vue'
import { PlusOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'

/**
 * 组件属性接口
 */
interface Props {
  /** 已存在的图片信息，如果提供则表示为编辑模式 */
  picture?: API.PictureVO
  /** 图片所属的空间ID */
  spaceId?: number
  /** 上传成功后的回调函数，接收新上传的图片信息 */
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

/** 上传加载状态 */
const loading = ref<boolean>(false)

/**
 * 处理图片上传
 * 将文件上传至后端服务器，支持新建和更新两种模式
 * 
 * @param options - 上传选项对象
 * @param options.file - 要上传的文件对象
 */
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    // 构建上传参数：如果是编辑模式则包含图片ID，否则为空对象
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    // 设置空间ID
    params.spaceId = props.spaceId
    // 调用上传接口
    const res = await uploadPictureUsingPost(params, {}, file)
    
    // 处理上传结果
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 触发成功回调，将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    message.error('图片上传失败')
  } finally {
    // 无论成功或失败，都要重置加载状态
    loading.value = false
  }
}

/**
 * 上传前的文件校验
 * 检查文件类型和大小是否符合要求
 * 
 * @param file - 待上传的文件对象
 * @returns {boolean} 返回 true 表示校验通过，可以上传；返回 false 表示校验失败，阻止上传
 */
const beforeUpload = (file: UploadProps['fileList'][number]) => {
  // 允许的图片类型：JPEG、PNG、WebP
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  const isAllowed = allowedTypes.includes((file as any).type)
  
  if (!isAllowed) {
    message.error('不支持上传该格式的图片，支持 jpg / png / webp')
  }
  
  // 检查文件大小：限制为 3MB
  const isLt2M = (file as any).size / 1024 / 1024 < 3
  if (!isLt2M) {
    message.error('不能上传超过 3M 的图片')
  }
  
  // 只有类型和大小都符合要求时才允许上传
  return isAllowed && isLt2M
}

</script>

<style scoped>
.picture-upload :deep(.ant-upload) {
  width: 100% !important;
  height: 100% !important;
  min-height: 152px;
  min-width: 152px;
}

.picture-upload img {
  max-width: 100%;
  max-height: 480px;
}

.ant-upload-select-picture-card i {
  font-size: 32px;
  color: #999;
}

.ant-upload-select-picture-card .ant-upload-text {
  margin-top: 8px;
  color: #666;
}

</style>
