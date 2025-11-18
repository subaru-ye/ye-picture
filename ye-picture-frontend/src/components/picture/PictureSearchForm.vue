<template>
  <div class="picture-search-form">
    <!-- 图片搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <!-- 关键词搜索 -->
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>

      <!-- 分类选择 -->
      <a-form-item label="分类" name="category">
        <a-auto-complete
          v-model:value="searchParams.category"
          style="min-width: 180px"
          :options="categoryOptions"
          placeholder="请输入分类"
          allowClear
        />
      </a-form-item>

      <!-- 标签选择（支持多选） -->
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="searchParams.tags"
          style="min-width: 180px"
          :options="tagOptions"
          mode="tags"
          placeholder="请输入标签"
          allowClear
        />
      </a-form-item>

      <!-- 编辑时间范围选择 -->
      <a-form-item label="日期" name="">
        <a-range-picker
          style="width: 400px"
          show-time
          v-model:value="dateRange"
          :placeholder="['编辑开始日期', '编辑结束时间']"
          format="YYYY/MM/DD HH:mm:ss"
          :presets="rangePresets"
          @change="onRangeChange"
        />
      </a-form-item>

      <!-- 图片名称搜索 -->
      <a-form-item label="名称" name="name">
        <a-input v-model:value="searchParams.name" placeholder="请输入名称" allow-clear />
      </a-form-item>

      <!-- 图片简介搜索 -->
      <a-form-item label="简介" name="introduction">
        <a-input v-model:value="searchParams.introduction" placeholder="请输入简介" allow-clear />
      </a-form-item>

      <!-- 图片宽度筛选 -->
      <a-form-item label="宽度" name="picWidth">
        <a-input-number v-model:value="searchParams.picWidth" />
      </a-form-item>

      <!-- 图片高度筛选 -->
      <a-form-item label="高度" name="picHeight">
        <a-input-number v-model:value="searchParams.picHeight" />
      </a-form-item>

      <!-- 图片格式筛选 -->
      <a-form-item label="格式" name="picFormat">
        <a-input v-model:value="searchParams.picFormat" placeholder="请输入格式" allow-clear />
      </a-form-item>

      <!-- 操作按钮 -->
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" style="width: 96px">搜索</a-button>
          <a-button html-type="reset" @click="doClear">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
/**
 * 图片搜索表单组件
 * 提供多条件搜索功能，支持关键词、分类、标签、日期范围、图片属性等筛选条件
 * 通过 onSearch 回调将搜索参数传递给父组件
 */
import { onMounted, reactive, ref } from 'vue'
import { listPictureTagCategoryUsingGet } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

// ==================== Props 定义 ====================
/**
 * 组件属性接口
 */
interface Props {
  /** 搜索回调函数，当用户点击搜索按钮时触发 */
  onSearch?: (searchParams: API.PictureQueryRequest) => void
}

const props = defineProps<Props>()

// ==================== 响应式数据 ====================
/** 搜索条件参数 */
const searchParams = reactive<API.PictureQueryRequest>({})
/** 日期范围选择器的值 */
const dateRange = ref<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)
/** 分类选项列表 */
const categoryOptions = ref<Array<{ value: string; label: string }>>([])
/** 标签选项列表 */
const tagOptions = ref<Array<{ value: string; label: string }>>([])

/** 日期范围预设选项 */
const rangePresets = ref([
  { label: '过去 7 天', value: [dayjs().add(-7, 'd'), dayjs()] },
  { label: '过去 14 天', value: [dayjs().add(-14, 'd'), dayjs()] },
  { label: '过去 30 天', value: [dayjs().add(-30, 'd'), dayjs()] },
  { label: '过去 90 天', value: [dayjs().add(-90, 'd'), dayjs()] },
])

// ==================== 方法 ====================
/**
 * 执行搜索操作
 * 将当前搜索参数通过 onSearch 回调传递给父组件
 */
const doSearch = () => {
  props.onSearch?.(searchParams)
}

/**
 * 处理日期范围变化
 * 将日期范围选择器的值转换为搜索参数中的开始和结束时间字符串
 * @param dates - 日期数组，包含开始和结束日期（dayjs 实例）
 * @param dateStrings - 日期字符串数组（未使用）
 */
const onRangeChange = (dates: [dayjs.Dayjs, dayjs.Dayjs] | null, dateStrings: string[]) => {
  if (!dates || dates.length < 2) {
    // 清空日期范围时，清除搜索参数中的时间条件
    searchParams.startEditTime = undefined
    searchParams.endEditTime = undefined
  } else {
    // 将 dayjs 对象格式化为字符串（例如：'YYYY-MM-DD HH:mm:ss'）
    searchParams.startEditTime = dates[0].format('YYYY-MM-DD HH:mm:ss')
    searchParams.endEditTime = dates[1].format('YYYY-MM-DD HH:mm:ss')
  }
}

/**
 * 获取标签和分类选项
 * 从后端接口获取所有可用的分类和标签列表，用于下拉选项
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
 * 重置搜索表单
 * 清空所有搜索条件和日期范围，并触发搜索回调
 */
const doClear = () => {
  // 清空所有搜索参数
  Object.keys(searchParams).forEach((key) => {
    searchParams[key as keyof API.PictureQueryRequest] = undefined
  })
  // 清空日期范围选择器
  dateRange.value = null
  // 触发搜索回调，使用清空后的参数
  props.onSearch?.(searchParams)
}

// ==================== 生命周期 ====================
/**
 * 组件挂载时初始化
 * 加载标签和分类选项
 */
onMounted(() => {
  getTagCategoryOptions()
})
</script>

<style scoped>
.picture-search-form .ant-form-item {
  margin-top: 16px;
}
</style>
