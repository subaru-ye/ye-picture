<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[16, 16]">
      <!-- 图片展示区 -->
      <a-col :sm="24" :md="16" :xl="18">
        <a-card title="图片预览">
          <a-image style="max-height: 600px; object-fit: contain" :src="picture.url" />
        </a-card>
      </a-col>
      <!-- 图片信息区 -->
      <a-col :sm="24" :md="8" :xl="6">
        <a-card title="图片信息">
          <a-descriptions :column="1">
            <!-- <a-descriptions-item label="作者">
              <a-space>
                <a-avatar :size="24" :src="picture.user?.userAvatar" />
                <div>{{ picture.user?.userName }}</div>
              </a-space>
            </a-descriptions-item> -->
            <a-descriptions-item label="名称">
              {{ picture.name ?? '未命名' }}
            </a-descriptions-item>
            <a-descriptions-item label="简介">
              {{ picture.introduction ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ picture.category ?? '默认' }}
            </a-descriptions-item>
            <a-descriptions-item label="标签">
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽度">
              {{ picture.picWidth ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="高度">
              {{ picture.picHeight ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
            <a-descriptions-item label="主色调">
              <a-space>
                {{ picture.picColor ?? '-' }}
                <div
                  v-if="picture.picColor"
                  :style="{
                    backgroundColor: toHexColor(picture.picColor),
                    width: '16px',
                    height: '16px',
                  }"
                />
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
          <!-- 操作按钮区 -->
          <a-space wrap>
            <a-button type="primary" @click="doDownload">
              免费下载
              <template #icon>
                <DownloadOutlined />
              </template>
            </a-button>
            <a-button type="primary" ghost @click="doShare">
              分享
              <template #icon>
                <ShareAltOutlined />
              </template>
            </a-button>
            <a-button v-if="canEdit" type="default" @click="doEdit">
              编辑
              <template #icon>
                <EditOutlined />
              </template>
            </a-button>
            <a-button v-if="canDelete" danger @click="doDelete">
              删除
              <template #icon>
                <DeleteOutlined />
              </template>
            </a-button>
          </a-space>
        </a-card>
      </a-col>
    </a-row>
    <!-- 分享弹窗 -->
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
/**
 * 图片详情页组件
 * 展示图片的详细信息，支持下载、分享、编辑、删除等操作
 */

// ==================== 导入依赖 ====================
import { onMounted, ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import router from '@/router'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import {
  deletePictureUsingPost,
  getPictureVoByIdUsingGet,
} from '@/api/pictureController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import ShareModal from '@/components/common/ShareModal.vue'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import { downloadImage, formatSize, toHexColor } from '@/utils'

// ==================== Props 定义 ====================
const props = defineProps<{
  /** 图片 ID */
  id: string | number
}>()

// ==================== 响应式数据 ====================
/** 图片详情数据 */
const picture = ref<API.PictureVO>({})
/** 登录用户状态管理 */
const loginUserStore = useLoginUserStore()
/** 分享弹窗组件引用 */
const shareModalRef = ref()
/** 分享链接 */
const shareLink = ref<string>()

// ==================== 计算属性 ====================
/**
 * 创建权限检查函数
 * @param permission - 权限标识
 * @returns 返回一个计算属性，用于检查当前用户是否拥有该权限
 */
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (picture.value.permissionList ?? []).includes(permission)
  })
}

/** 是否拥有编辑权限 */
const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
/** 是否拥有删除权限 */
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// ==================== 数据获取方法 ====================
/**
 * 获取图片详情
 * 根据图片 ID 从后端获取图片的详细信息
 */
const fetchPictureDetail = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

// ==================== 操作方法 ====================
/**
 * 下载图片
 * 触发图片下载功能
 */
const doDownload = async () => {
  if (!picture.value.url) {
    message.warning('图片地址不存在')
    return
  }
  
  try {
    // 使用图片名称作为文件名，如果没有则使用默认名称
    const fileName = picture.value.name 
      ? `${picture.value.name}${picture.value.picFormat ? '.' + picture.value.picFormat : ''}`
      : undefined
    await downloadImage(picture.value.url, fileName)
    // 下载操作成功触发后显示成功提示
    message.success('下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请稍后重试')
  }
}

/**
 * 分享图片
 * 生成分享链接并打开分享弹窗
 */
const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.value.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

/**
 * 编辑图片
 * 跳转到图片编辑页面，携带图片 ID 和空间 ID
 */
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: {
      id: picture.value.id,
      spaceId: picture.value.spaceId,
    },
  })
}

/**
 * 删除图片
 * 调用删除接口删除当前图片
 */
const doDelete = async () => {
  const id = picture.value.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
  } else {
    message.error('删除失败')
  }
}

// ==================== 生命周期钩子 ====================
/**
 * 组件挂载时获取图片详情
 */
onMounted(() => {
  fetchPictureDetail()
})
</script>

<style scoped>
#pictureDetailPage {
  margin-bottom: 16px;
}
</style>
