<template>
  <div id="addPicturePage">
    <h2 style="margin-bottom: 16px">
      {{ route.query?.id ? '修改图片' : '创建图片' }}
    </h2>
    <a-typography-paragraph v-if="spaceId" type="secondary">
      保存至空间：<a :href="`/space/${spaceId}`" target="_blank">{{ spaceId }}</a>
    </a-typography-paragraph>

    <!-- 图片详情加载中提示 -->
    <div v-if="!picture && route.query?.id" style="text-align: center; padding: 40px 0">
      <a-spin size="large" tip="加载图片详情中..."></a-spin>
    </div>

    <!-- 选择上传方式 -->
    <a-tabs v-model:activeKey="uploadType">
      <a-tab-pane key="file" tab="文件上传">
        <PictureUpload :picture="picture" :spaceId="spaceId" :onSuccess="onSuccess" />
      </a-tab-pane>
      <a-tab-pane key="url" tab="URL 上传" force-render>
        <UrlPictureUpload :picture="picture" :spaceId="spaceId" :onSuccess="onSuccess" />
      </a-tab-pane>
    </a-tabs>

    <!-- 图片编辑操作区 -->
    <div v-if="picture" class="edit-bar">
      <a-space size="middle">
        <a-button :icon="h(EditOutlined)" @click="doEditPicture">编辑图片</a-button>
        <a-button type="primary" ghost :icon="h(FullscreenOutlined)" @click="doImagePainting">
          AI 扩图
        </a-button>
      </a-space>
      <ImageCropper
        ref="imageCropperRef"
        :imageUrl="picture?.url"
        :picture="picture"
        :spaceId="spaceId"
        :space="space"
        :onSuccess="onSuccess"
      />
      <ImageOutPainting
        ref="imageOutPaintingRef"
        :picture="picture"
        :spaceId="spaceId"
        :onSuccess="onImageOutPaintingSuccess"
      />
    </div>

    <!-- 图片信息编辑表单 -->
    <a-form v-if="picture" layout="vertical" :model="pictureForm" @finish="handleSubmit">
      <a-form-item label="名称" name="name">
        <a-input v-model:value="pictureForm.name" placeholder="请输入名称" />
      </a-form-item>
      <a-form-item label="简介" name="introduction">
        <a-textarea
          v-model:value="pictureForm.introduction"
          placeholder="请输入简介"
          :rows="2"
          autoSize
          allowClear
        />
      </a-form-item>
      <a-form-item label="分类" name="category">
        <a-auto-complete
          v-model:value="pictureForm.category"
          :options="categoryOptions"
          placeholder="请输入分类"
          allowClear
        />
      </a-form-item>
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="pictureForm.tags"
          :options="tagOptions"
          mode="tags"
          placeholder="请输入标签"
          allowClear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">
          {{ route.query?.id ? '保存修改' : '创建' }}
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
/**
 * 添加/编辑图片页面组件
 * 支持文件上传和 URL 上传两种方式，提供图片信息编辑、裁剪、AI 扩图等功能
 */

// ==================== 导入依赖 ====================
import { computed, h, onMounted, reactive, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { EditOutlined, FullscreenOutlined } from '@ant-design/icons-vue'
import {
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import PictureUpload from '@/components/picture/PictureUpload.vue'
import UrlPictureUpload from '@/components/picture/UrlPictureUpload.vue'
import ImageCropper from '@/components/picture/ImageCropper.vue'
import ImageOutPainting from '@/components/picture/ImageOutPainting.vue'

// ==================== 响应式数据 ====================
/** 当前图片信息 */
const picture = ref<API.PictureVO>()
/** 图片编辑表单数据 */
const pictureForm = reactive<API.PictureEditRequest>({})
/** 上传方式：文件上传或 URL 上传 */
const uploadType = ref<'file' | 'url'>('file')
/** 空间信息 */
const space = ref<API.SpaceVO>()
/** 分类选项列表 */
const categoryOptions = ref<Array<{ value: string; label: string }>>([])
/** 标签选项列表 */
const tagOptions = ref<Array<{ value: string; label: string }>>([])

// ==================== 路由和组件引用 ====================
const router = useRouter()
const route = useRoute()
/** 图片裁剪组件引用 */
const imageCropperRef = ref()
/** AI 扩图组件引用 */
const imageOutPaintingRef = ref()

// ==================== 计算属性 ====================
/**
 * 空间 ID
 * 优先从图片信息获取，其次从路由参数获取
 */
const spaceId = computed(() => {
  return picture.value?.spaceId || route.query?.spaceId
})

// ==================== 数据获取方法 ====================
/**
 * 获取标签和分类选项
 * 从后端接口获取所有可用的分类和标签列表
 */
const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      // 转换成下拉选项组件接受的格式
      tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => ({
        value: data,
        label: data,
      }))
      categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => ({
        value: data,
        label: data,
      }))
    } else {
      message.error('加载选项失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('加载选项失败：' + error.message)
  }
}

/**
 * 获取图片详情
 * 根据路由参数中的图片 ID 加载图片信息
 */
const getOldPicture = async () => {
  const id = route.query?.id
  if (!id) {
    return
  }

  try {
    const res = await getPictureVoByIdUsingGet({ id: id })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      picture.value = data
      pictureForm.name = data.name
      pictureForm.introduction = data.introduction
      pictureForm.category = data.category
      pictureForm.tags = data.tags
    } else {
      message.error('加载图片详情失败，' + res.data.message)
      if (spaceId.value) {
        router.push(`/space/${spaceId.value}`)
      }
    }
  } catch (error: any) {
    message.error('加载图片详情异常：' + error.message)
    if (spaceId.value) {
      router.push(`/space/${spaceId.value}`)
    }
  }
}

/**
 * 获取空间信息
 * 根据空间 ID 加载空间详细信息
 */
const fetchSpace = async () => {
  const currentSpaceId = spaceId.value
  if (!currentSpaceId) {
    space.value = undefined
    return
  }

  try {
    const res = await getSpaceVoByIdUsingGet({ id: currentSpaceId })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    }
  } catch (error: any) {
    console.error('加载空间信息失败：', error)
    message.error('加载空间信息失败：' + error.message)
  }
}

// ==================== 事件处理方法 ====================
/**
 * 图片上传成功回调
 * @param newPicture - 新上传的图片信息
 */
const onSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
  pictureForm.name = newPicture.name
}

/**
 * 提交表单
 * 保存图片的编辑信息（名称、简介、分类、标签等）
 * @param values - 表单数据
 */
const handleSubmit = async (values: any) => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    message.warning('请先上传图片')
    return
  }

  try {
    const res = await editPictureUsingPost({
      id: pictureId,
      spaceId: spaceId.value,
      ...values,
    })

    if (res.data.code === 0 && res.data.data) {
      message.success(route.query?.id ? '修改成功' : '创建成功')
      // 跳转到图片详情页
      router.push({
        path: `/picture/${pictureId}`,
      })
    } else {
      message.error(`${route.query?.id ? '修改' : '创建'}失败，${res.data.message}`)
    }
  } catch (error: any) {
    message.error(`${route.query?.id ? '修改' : '创建'}失败：${error.message}`)
  }
}

/**
 * 打开图片编辑弹窗
 * 用于裁剪图片
 */
const doEditPicture = () => {
  if (imageCropperRef.value) {
    imageCropperRef.value.openModal()
  }
}

/**
 * 打开 AI 扩图弹窗
 */
const doImagePainting = () => {
  if (imageOutPaintingRef.value) {
    imageOutPaintingRef.value.openModal()
  }
}

/**
 * AI 扩图成功回调
 * @param newPicture - 扩图后的新图片信息
 */
const onImageOutPaintingSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// ==================== 生命周期和监听器 ====================
/**
 * 组件挂载时初始化
 * 加载标签分类选项和图片详情
 */
onMounted(() => {
  getTagCategoryOptions()
  getOldPicture()
})

/**
 * 监听路由参数中的图片 ID 变化
 * 当 ID 变化时重新加载图片详情
 */
watch(
  () => route.query.id,
  (newId) => {
    if (newId) {
      // 重置表单状态，避免残留旧数据
      pictureForm.name = ''
      pictureForm.introduction = ''
      pictureForm.category = ''
      pictureForm.tags = []
      picture.value = undefined
      // 重新加载图片详情
      getOldPicture()
    }
  }
)

/**
 * 监听 spaceId 变化
 * 当 spaceId 变化时自动获取空间信息
 */
watchEffect(() => {
  fetchSpace()
})

/**
 * 监听图片的空间 ID 变化
 * 当图片加载完成后，如果空间 ID 发生变化，重新获取空间信息
 */
watch(
  () => picture.value?.spaceId,
  (newSpaceId, oldSpaceId) => {
    if (newSpaceId !== oldSpaceId) {
      fetchSpace()
    }
  }
)
</script>

<style scoped>
#addPicturePage {
  max-width: 720px;
  margin: 0 auto;
}

#addPicturePage .edit-bar {
  text-align: center;
  margin: 16px 0;
}
</style>
