<template>
  <div class="picture-search-form">
    <!-- 图片搜索表单：支持关键词、分类、标签等多条件组合搜索 -->
    <a-form layout="inline" :model="searchParams">
      <!-- 关键词搜索：模糊匹配图片名称和简介 -->
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
          @clear="triggerSearch"
        />
      </a-form-item>

      <!-- 分类筛选：使用自动补全下拉 -->
      <a-form-item label="分类" name="category">
        <a-auto-complete
          v-model:value="searchParams.category"
          style="min-width: 180px"
          :options="categoryOptions"
          placeholder="请输入分类"
          allowClear
          @clear="triggerSearch"
          @select="triggerSearch"
        />
      </a-form-item>

      <!-- 标签筛选：支持多选标签 -->
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="searchParams.tags"
          style="min-width: 180px"
          :options="tagOptions"
          mode="tags"
          placeholder="请输入标签"
          allowClear
          @clear="triggerSearch"
          @change="triggerSearch"
        />
      </a-form-item>

      <!-- 操作按钮组 -->
      <a-form-item>
        <a-space>
          <a-button type="primary" @click="triggerSearch" style="width: 96px">搜索</a-button>
          <a-button @click="doClear">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
/**
 * 图片搜索表单组件
 */
import { onMounted, reactive, ref } from 'vue'
import { listPictureTagCategoryUsingGet } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

// ==================== Props 定义 ====================
interface Props {
  /**
   * 搜索触发回调函数
   */
  onSearch?: (searchParams: API.PictureQueryRequest) => void
}
const props = defineProps<Props>()

// ==================== 响应式数据 ====================
const searchParams = reactive<API.PictureQueryRequest>({})
const categoryOptions = ref<Array<{ value: string; label: string }>>([])
const tagOptions = ref<Array<{ value: string; label: string }>>([])

// ==================== 方法 ====================
/**
 * 统一触发搜索的入口
 * 将当前 searchParams 传递给父组件
 */
const triggerSearch = () => {
  props.onSearch?.({ ...searchParams }) // 使用展开避免引用问题（可选，但更安全）
}

/**
 * 重置所有搜索条件并触发空搜索
 */
const doClear = () => {
  Object.keys(searchParams).forEach((key) => {
    searchParams[key as keyof API.PictureQueryRequest] = undefined
  })
  triggerSearch()
}

/**
 * 加载分类和标签候选选项
 */
const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
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

onMounted(() => {
  getTagCategoryOptions()
})
</script>

<style scoped>
.picture-search-form .ant-form-item {
  margin-top: 16px;
}
</style>
