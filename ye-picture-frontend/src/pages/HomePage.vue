<template>
  <div id="homePage">
    <!-- 搜索框：支持关键词搜索 -->
    <div class="search-bar">
      <div class="search-wrapper">
      <a-input-search
        placeholder="从海量图片中搜索"
        v-model:value="searchParams.searchText"
        enter-button="搜索"
        size="large"
          allow-clear
        @search="doSearch"
          @focus="showSearchHistory = true"
          @blur="handleSearchBlur"
          @clear="handleSearchClear"
        />
        <!-- 搜索历史下拉列表 -->
        <div v-if="showSearchHistory && searchHistoryList.length > 0" class="search-history">
          <div class="search-history-header">
            <span>搜索历史</span>
            <a-button type="link" size="small" @click="clearSearchHistory">清除</a-button>
          </div>
          <div class="search-history-list">
            <div
              v-for="(history, index) in searchHistoryList"
              :key="index"
              class="search-history-item"
              @click="selectSearchHistory(history)"
            >
              <div class="history-content">
                <div class="history-keyword">
                  <span v-if="history.searchText">{{ history.searchText }}</span>
                  <span v-else class="empty-text">（无关键词）</span>
                </div>
                <div class="history-filters">
                  <a-tag v-if="history.category && history.category !== 'all'" size="small" color="blue">
                    {{ history.category }}
                  </a-tag>
                  <a-tag
                    v-for="tag in history.tags"
                    :key="tag"
                    size="small"
                    color="green"
                  >
                    {{ tag }}
                  </a-tag>
                </div>
              </div>
              <a-button
                type="text"
                size="small"
                class="delete-btn"
                @click.stop="deleteSearchHistory(index)"
              >
                删除
              </a-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!-- 分类标签页：支持按分类筛选（仅显示前5条） -->
    <a-tabs v-model:activeKey="selectedCategory" @change="doSearch">
      <a-tab-pane key="all" tab="全部" />
      <a-tab-pane v-for="category in displayedCategoryList" :key="category" :tab="category" />
    </a-tabs>
    <!-- 标签选择区：支持多标签筛选（仅显示前5条） -->
    <div class="tag-bar">
      <span style="margin-right: 8px">标签：</span>
      <a-space :size="[0, 8]" wrap>
        <a-checkable-tag
          v-for="(tag, index) in displayedTagList"
          :key="tag"
          v-model:checked="selectedTagList[index]"
          @change="doSearch"
        >
          {{ tag }}
        </a-checkable-tag>
      </a-space>
    </div>

    <!-- 图片列表：展示搜索结果 -->
    <PictureList :dataList="dataList" :loading="loading" />
    <!-- 分页组件：支持分页浏览 -->
    <a-pagination
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
    />

  </div>
</template>

<script setup lang="ts">
/**
 * 首页组件
 * 提供图片搜索、分类筛选、标签筛选和分页浏览功能
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/picture/PictureList.vue'

/** 图片数据列表 */
const dataList = ref<API.PictureVO[]>([])
/** 数据总数 */
const total = ref(0)
/** 加载状态 */
const loading = ref(true)

/** 搜索条件参数 */
const searchParams = reactive<API.QueryPictureRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

/** 分类列表 */
const categoryList = ref<string[]>([])
/** 当前选中的分类 */
const selectedCategory = ref<string>('all')
/** 标签列表 */
const tagList = ref<string[]>([])
/** 选中的标签状态列表（布尔数组，对应 tagList 的索引） */
const selectedTagList = ref<boolean[]>([])

/** 最大显示分类数量 */
const MAX_DISPLAY_CATEGORY_COUNT = 5
/** 最大显示标签数量 */
const MAX_DISPLAY_TAG_COUNT = 5

/**
 * 显示的分类列表（仅显示前5条）
 */
const displayedCategoryList = computed(() => {
  return categoryList.value.slice(0, MAX_DISPLAY_CATEGORY_COUNT)
})

/**
 * 显示的标签列表（仅显示前5条）
 */
const displayedTagList = computed(() => {
  return tagList.value.slice(0, MAX_DISPLAY_TAG_COUNT)
})

/** 搜索历史列表 */
interface SearchHistoryItem {
  searchText?: string
  category?: string
  tags?: string[]
  timestamp: number
}
const searchHistoryList = ref<SearchHistoryItem[]>([])
/** 是否显示搜索历史 */
const showSearchHistory = ref(false)
/** 搜索历史存储的 key */
const SEARCH_HISTORY_KEY = 'picture_search_history'
/** 最大保存历史记录数量 */
const MAX_HISTORY_COUNT = 10
/** 是否已初始化完成，用于避免初始化时触发 watch */
const isInitialized = ref(false)

/**
 * 获取图片列表数据
 * 根据搜索条件、分类和标签筛选获取分页数据
 */
const fetchData = async () => {
  loading.value = true
  try {
    // 构建搜索参数
    const params: API.QueryPictureRequest = {
      ...searchParams,
      tags: [] as string[],
    }

    // 如果选择了分类（非"全部"），则添加到搜索参数中
    if (selectedCategory.value !== 'all') {
      params.category = selectedCategory.value
    }

    // 将选中的标签添加到搜索参数中（只处理显示的标签）
    displayedTagList.value.forEach((tag, index) => {
      if (selectedTagList.value[index]) {
        params.tags!.push(tag)
      }
    })

    // 调用接口获取数据
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

/**
 * 处理分页变化事件
 * @param page - 当前页码
 * @param pageSize - 每页显示数量
 */
const onPageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  searchParams.pageSize = pageSize
  void fetchData()
}

/**
 * 执行搜索操作
 * 重置页码到第一页并重新获取数据
 */
const doSearch = () => {
  // 重置页码到第一页
  searchParams.current = 1
  // 保存搜索历史
  saveSearchHistory()
  void fetchData()
}

/**
 * 保存搜索历史到 localStorage
 */
const saveSearchHistory = () => {
  // 获取当前搜索参数
  const currentSearch: SearchHistoryItem = {
    searchText: searchParams.searchText,
    category: selectedCategory.value !== 'all' ? selectedCategory.value : undefined,
    tags: [] as string[],
    timestamp: Date.now(),
  }

  // 收集选中的标签
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) {
      currentSearch.tags!.push(tagList.value[index])
    }
  })

  // 如果没有标签，删除空数组
  if (currentSearch.tags!.length === 0) {
    delete currentSearch.tags
  }

  // 检查是否为空搜索（无关键词、无分类、无标签）
  const isEmptySearch = !currentSearch.searchText &&
                        !currentSearch.category &&
                        (!currentSearch.tags || currentSearch.tags.length === 0)

  // 空搜索不保存
  if (isEmptySearch) {
    return
  }

  // 从 localStorage 读取历史记录
  const historyStr = localStorage.getItem(SEARCH_HISTORY_KEY)
  let historyList: SearchHistoryItem[] = historyStr ? JSON.parse(historyStr) : []

  // 检查是否已存在相同的搜索（比较搜索参数）
  const existingIndex = historyList.findIndex(item => {
    return item.searchText === currentSearch.searchText &&
           item.category === currentSearch.category &&
           JSON.stringify(item.tags || []) === JSON.stringify(currentSearch.tags || [])
  })

  // 如果已存在，先删除旧的
  if (existingIndex !== -1) {
    historyList.splice(existingIndex, 1)
  }

  // 将当前搜索添加到最前面
  historyList.unshift(currentSearch)

  // 限制历史记录数量
  if (historyList.length > MAX_HISTORY_COUNT) {
    historyList = historyList.slice(0, MAX_HISTORY_COUNT)
  }

  // 保存到 localStorage
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(historyList))

  // 更新当前历史列表
  searchHistoryList.value = historyList
}

/**
 * 从 localStorage 加载搜索历史
 */
const loadSearchHistory = () => {
  try {
    const historyStr = localStorage.getItem(SEARCH_HISTORY_KEY)
    if (historyStr) {
      searchHistoryList.value = JSON.parse(historyStr)
    }
  } catch (error) {
    console.error('加载搜索历史失败:', error)
    searchHistoryList.value = []
  }
}

/**
 * 选择搜索历史项
 * 恢复搜索参数并执行搜索
 */
const selectSearchHistory = (history: SearchHistoryItem) => {
  // 恢复搜索关键词
  searchParams.searchText = history.searchText || ''

  // 恢复分类
  selectedCategory.value = history.category || 'all'

  // 恢复标签选择状态（基于显示的标签列表）
  const displayCount = Math.min(MAX_DISPLAY_TAG_COUNT, tagList.value.length)
  selectedTagList.value = new Array(displayCount).fill(false)
  if (history.tags && history.tags.length > 0) {
    history.tags.forEach(tag => {
      const index = displayedTagList.value.indexOf(tag)
      if (index !== -1 && index < displayCount) {
        selectedTagList.value[index] = true
      }
    })
  }

  // 隐藏历史记录列表
  showSearchHistory.value = false

  // 执行搜索
  searchParams.current = 1
  void fetchData()
}

/**
 * 删除单个搜索历史项
 */
const deleteSearchHistory = (index: number) => {
  searchHistoryList.value.splice(index, 1)
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(searchHistoryList.value))
}

/**
 * 清除所有搜索历史
 */
const clearSearchHistory = () => {
  searchHistoryList.value = []
  localStorage.removeItem(SEARCH_HISTORY_KEY)
  showSearchHistory.value = false
}

/**
 * 处理搜索框失焦事件
 * 延迟隐藏历史记录，以便点击历史项时能触发点击事件
 */
const handleSearchBlur = () => {
  setTimeout(() => {
    showSearchHistory.value = false
  }, 200)
}

/**
 * 处理搜索框清空事件
 * 清空搜索框内容并重新搜索
 */
const handleSearchClear = () => {
  searchParams.searchText = ''
  // 清空后自动搜索，展示所有图片
  searchParams.current = 1
  void fetchData()
}

/**
 * 获取标签和分类选项
 * 从后端接口获取所有可用的分类和标签列表
 */
const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      // 更新分类和标签列表
      categoryList.value = res.data.data.categoryList ?? []
      tagList.value = res.data.data.tagList ?? []
      // 初始化选中标签状态列表（基于显示的标签数量，全部设为未选中）
      // 注意：这里使用 Math.min 确保不会超出实际标签数量
      const displayCount = Math.min(MAX_DISPLAY_TAG_COUNT, tagList.value.length)
      selectedTagList.value = new Array(displayCount).fill(false)
    } else {
      message.error('加载分类标签失败，' + res.data.message)
    }
  } catch (error) {
    message.error('加载分类标签失败')
  }
}

/**
 * 实时监控搜索参数
 * 当搜索框为空时，自动重新展示所有图片
 */
watch(
  () => searchParams.searchText,
  (newVal, oldVal) => {
    // 只在初始化完成后才监控
    if (!isInitialized.value) {
      return
    }

    // 如果搜索框被清空（从有值变为空），自动搜索
    if (oldVal && !newVal) {
      searchParams.current = 1
      void fetchData()
    }
  }
)

/**
 * 组件挂载时的初始化操作
 * 加载分类标签选项和初始图片数据
 */
onMounted(async () => {
  // 加载搜索历史
  loadSearchHistory()
  await getTagCategoryOptions()
  await fetchData()
  // 标记初始化完成
  isInitialized.value = true
})
</script>
<style scoped>
#homePage {
  margin-bottom: 16px;
}
#homePage .search-bar {
  max-width: 480px;
  margin: 0 auto 16px;
}
#homePage .search-wrapper {
  position: relative;
}
#homePage .search-history {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  margin-top: 4px;
  max-height: 400px;
  overflow-y: auto;
}
#homePage .search-history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
  color: #8c8c8c;
}
#homePage .search-history-list {
  padding: 4px 0;
}
#homePage .search-history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}
#homePage .search-history-item:hover {
  background-color: #f5f5f5;
}
#homePage .search-history-item .history-content {
  flex: 1;
  min-width: 0;
}
#homePage .search-history-item .history-keyword {
  font-size: 14px;
  color: #262626;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
#homePage .search-history-item .history-keyword .empty-text {
  color: #bfbfbf;
  font-style: italic;
}
#homePage .search-history-item .history-filters {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
#homePage .search-history-item .delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}
#homePage .search-history-item:hover .delete-btn {
  opacity: 1;
}
#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>
